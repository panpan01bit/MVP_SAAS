package com.toolfix.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toolfix.domain.KnowledgeBase;
import com.toolfix.domain.Manual;
import com.toolfix.domain.Product;
import com.toolfix.repository.KnowledgeBaseRepository;
import com.toolfix.repository.ManualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手册 AI 训练服务：上传的说明书 → 逐页"阅读" → 提取产品信息 →
 * 生成"客户可能遇到的问题清单" → 自动入库知识库（挂 SKU）。
 *
 * 双通道：
 *  - PDF 可提取文字 → 文本模型直读（快）
 *  - PDF 文字转曲/扫描件（无法提取） → 每页渲染成图片 → 视觉模型逐批读取（本 Demo 的真实手册即此类）
 * LLM 不可用时降级到旧的正则 Mock 解析。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ManualTrainingService {

    private static final int VISION_WORKERS = 4;
    private static final int MAX_IMAGE_WIDTH = 1500;
    private static final int MAX_PAGES = 40;
    private static final int TEXT_MODE_MIN_CHARS = 300;

    private final ManualRepository manualRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final LlmService llmService;
    private final MockManualParsingService mockFallback;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    public void trainAsync(Long manualId) {
        Manual manual = manualRepository.findById(manualId).orElse(null);
        if (manual == null) {
            log.error("训练失败: 手册不存在 id={}", manualId);
            return;
        }

        if (!llmService.isAvailable()) {
            log.info("LLM 未配置，手册 {} 降级为 Mock 解析", manualId);
            updateStatus(manual, "READING", 10, "未配置 AI Key，使用本地规则解析...");
            mockFallback.parseManualAsync(manualId);
            return;
        }

        try (PDDocument doc = Loader.loadPDF(new File(manual.getStoredFileName()))) {
            int pages = Math.min(doc.getNumberOfPages(), MAX_PAGES);

            // ---------- 阶段1: 阅读 ----------
            updateStatus(manual, "READING", 5, "打开 PDF，共 " + pages + " 页...");

            String rawText = new PDFTextStripper().getText(doc);
            String digest;
            if (rawText != null && rawText.strip().length() >= TEXT_MODE_MIN_CHARS) {
                log.info("手册 {} 含可提取文字({}字)，走文本通道", manualId, rawText.length());
                digest = llmService.chatText(
                        "你是电动工具售后技术专家。以下是产品说明书全文，请整理成结构化摘要：" +
                        "产品名称/型号/电池规格/功率、重要安全警告、关键操作步骤、常见故障排查、保修条款。用中文。",
                        truncate(rawText, 60000), 0.3, 4000);
                updateStatus(manual, "READING", 60, "全文阅读完成");
            } else {
                log.info("手册 {} 无法提取文字(转曲/扫描件, {}字)，走视觉通道", manualId,
                        rawText == null ? 0 : rawText.length());
                digest = readByVision(doc, pages, manual);
            }
            manual.setContentDigest(digest);
            manualRepository.save(manual);

            // ---------- 阶段2: 生成可能问题 ----------
            updateStatus(manual, "GENERATING", 75, "AI 正在分析手册，生成客户可能遇到的问题清单...");
            String issuesRaw = llmService.chatText(
                    buildGenerationSystemPrompt(), buildGenerationUserPrompt(manual, digest),
                    0.5, 4000);
            JsonNode issuesRoot = llmService.extractJson(issuesRaw);
            manual.setPossibleIssuesJson(issuesRoot.toString());

            // ---------- 阶段3: 落库 ----------
            applyExtractedFields(manual, issuesRoot);
            int created = saveKnowledgeEntries(manual, issuesRoot);

            manual.setParseStatus("DONE");
            manual.setParseProgress(100);
            manual.setParseMessage("训练完成：阅读 " + pages + " 页，生成 " + created + " 个可能问题并入库知识库");
            manualRepository.save(manual);
            log.info("手册 {} 训练完成, 生成 {} 条知识", manualId, created);

        } catch (Exception e) {
            log.error("手册训练失败 id={}", manualId, e);
            Manual m = manualRepository.findById(manualId).orElse(null);
            if (m != null) {
                m.setParseStatus("FAILED");
                m.setParseMessage("训练失败: " + e.getMessage());
                m.setParseErrorMessage(e.getMessage());
                manualRepository.save(m);
            }
        }
    }

    /** 视觉通道：逐页渲染成图，4 路并发让视觉模型阅读，最后合并摘要 */
    private String readByVision(PDDocument doc, int pages, Manual manual) throws Exception {
        PDFRenderer renderer = new PDFRenderer(doc);
        
        // 1) 顺序预渲染所有页(PDFRenderer 非线程安全)
        List<String> pageImages = new ArrayList<>();
        for (int p = 0; p < pages; p++) {
            try {
                pageImages.add(renderPageAsDataUri(renderer, p));
            } catch (Exception e) {
                log.warn("第 {} 页渲染失败: {}", p + 1, e.getMessage());
                pageImages.add(null);
            }
        }
        
        // 2) 并发调视觉模型(页与页相互独立)
        ExecutorService pool = Executors.newFixedThreadPool(VISION_WORKERS);
        AtomicInteger completed = new AtomicInteger();
        List<Future<String>> futures = new ArrayList<>();
        
        for (int p = 0; p < pages; p++) {
            final String image = pageImages.get(p);
            final int pageNo = p + 1;
            futures.add(pool.submit(() -> {
                if (image == null) return "";
                String partial = llmService.chatVision(
                        "这是某电动工具说明书的第" + pageNo + "页(共" + pages + "页)。" +
                        "请把本页中对售后诊断有价值的内容完整转述：产品名称/型号/规格参数/电池信息、" +
                        "安全警告、操作步骤、故障排查(Troubleshooting)、维护保养、保修条款。中文输出，保留数字与型号细节。",
                        List.of(image), 2500);
                int done = completed.incrementAndGet();
                updateStatus(manual, "READING", 5 + (int) (55.0 * done / pages),
                        "AI 阅读进度: " + done + "/" + pages + " 页");
                return "【第" + pageNo + "页】\n" + partial + "\n";
            }));
        }
        
        StringBuilder digest = new StringBuilder();
        for (Future<String> f : futures) {
            digest.append(f.get());
        }
        pool.shutdown();
        
        // 3) 合并各页摘要
        String merged = llmService.chatText(
                "以下是逐页读取某电动工具说明书得到的原始摘要，请去重、合并成一份连贯的产品手册技术摘要，" +
                "保留：名称/型号/规格/电池、安全警告要点、操作要点、故障排查条目、保修条款。中文输出。",
                truncate(digest.toString(), 60000), 0.3, 4000);
        updateStatus(manual, "READING", 65, "手册内容整理完成");
        return merged;
    }

    private String renderPageAsDataUri(PDFRenderer renderer, int pageIndex) throws Exception {
        BufferedImage image = renderer.renderImage(pageIndex, 110f / 72f);
        if (image.getWidth() > MAX_IMAGE_WIDTH) {
            float scale = (float) MAX_IMAGE_WIDTH / image.getWidth();
            image = renderer.renderImage(pageIndex, 110f / 72f * scale);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        log.info("手册页 {} 渲染完成: {}KB", pageIndex + 1, b64.length() / 1024);
        return "data:image/jpeg;base64," + b64;
    }

    private String buildGenerationSystemPrompt() {
        return """
                你是跨境电商电动工具售后 SaaS 平台的资深知识工程师。基于说明书摘要，站在客服角度预测"客户最可能遇到的问题"。

                要求：
                1. 生成 10 个问题，其中约 7 个是"假故障"(用户误操作导致，可通过引导自行解决，如电池休眠、转向锁死、档位不对等)，约 3 个是真故障(需转人工)。
                2. 问题必须紧扣说明书内容(该产品的电池类型、档位、部件)，不要泛泛而谈。
                3. troubleshootingSteps 引用说明书中的实际操作，英文，编号步骤，每步一句话。
                4. keywords 为英文逗号分隔，是客户描述该问题时会用到的词。
                5. 严格只输出 JSON，不要其他文字，格式：
                {"productName":"中文名","productNameEn":"English Name","model":"型号","batteryInfo":"...","powerInfo":"...",
                 "safetyWarnings":"...","warrantyTerms":"...",
                 "issues":[{"titleZh":"电池进入休眠模式","titleEn":"Battery in Sleep Mode",
                   "symptomEn":"Battery won't charge and LED does not light up",
                   "rootCauseZh":"长期未使用触发保护","rootCauseEn":"Protection mode after long storage",
                   "stepsEn":"1. ... 2. ...","keywords":"battery,not charge,led,sleep","isFalseFault":true}]}
                """;
    }

    private String buildGenerationUserPrompt(Manual manual, String digest) {
        Product p = manual.getProduct();
        return "产品: " + p.getProductName() + " (SKU: " + p.getSku() + ")\n"
                + "说明书摘要:\n" + truncate(digest, 30000);
    }

    private void applyExtractedFields(Manual manual, JsonNode root) {
        manual.setExtractedProductName(txt(root, "productName"));
        manual.setExtractedModel(txt(root, "model"));
        manual.setExtractedBatteryInfo(txt(root, "batteryInfo"));
        manual.setExtractedPowerInfo(txt(root, "powerInfo"));
        manual.setExtractedSafetyWarnings(txt(root, "safetyWarnings"));
        manual.setExtractedWarrantyTerms(txt(root, "warrantyTerms"));
    }

    private int saveKnowledgeEntries(Manual manual, JsonNode root) {
        Product product = manual.getProduct();
        JsonNode issues = root.withArray("issues");
        int created = 0;
        for (JsonNode issue : issues) {
            String titleEn = txt(issue, "titleEn");
            if (titleEn.isBlank()) continue;

            KnowledgeBase kb = new KnowledgeBase();
            kb.setScenarioName(txt(issue, "titleZh").isBlank() ? titleEn : txt(issue, "titleZh"));
            kb.setSymptomDescription(txt(issue, "symptomEn"));
            kb.setRootCause(txt(issue, "rootCauseZh").isBlank() ? txt(issue, "rootCauseEn") : txt(issue, "rootCauseZh"));
            kb.setTroubleshootingSteps(txt(issue, "stepsEn"));
            kb.setKeywords(txt(issue, "keywords"));
            kb.setGuidePageSlug(slugify(titleEn) + "-" + product.getSku().toLowerCase(Locale.ROOT));
            kb.setType(KnowledgeBase.KnowledgeType.SKU_SPECIFIC);
            kb.setRelatedSku(product.getSku());
            kb.setActive(true);
            knowledgeBaseRepository.save(kb);
            created++;
        }
        return created;
    }

    private void updateStatus(Manual manual, String status, int progress, String message) {
        manual.setParseStatus(status);
        manual.setParseProgress(progress);
        manual.setParseMessage(message);
        manualRepository.save(manual);
    }

    private static String txt(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }

    private static String slugify(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
