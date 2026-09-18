package com.toolfix.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.toolfix.domain.DiagnosisSession;
import com.toolfix.domain.KnowledgeBase;
import com.toolfix.domain.Manual;
import com.toolfix.domain.Message;
import com.toolfix.domain.Product;
import com.toolfix.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 诊断门面：优先用真实 LLM（文本模型）做多轮诊断，
 * 输出统一为 DiagnosisResponse；LLM 不可用或调用失败时降级为关键词 Mock。
 * 高危词拦截发生在更上游的 Controller（优先级最高，不会进到这里）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiDiagnosisService {

    private final LlmService llmService;
    private final MockAIDiagnosisService mockFallback;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Value("${toolfix.ai.max-conversation-rounds}")
    private int maxConversationRounds;

    public DiagnosisResponse diagnose(DiagnosisSession session, String userMessage,
                                      List<Message> history, Manual manual) {
        if (llmService.isAvailable()) {
            try {
                return diagnoseWithLlm(session, userMessage, history, manual);
            } catch (Exception e) {
                log.warn("LLM 诊断失败, 降级 Mock: {}", e.getMessage());
            }
        }
        MockAIDiagnosisService.DiagnosisResponse mock =
                mockFallback.diagnose(session, userMessage, history, manual);
        return new DiagnosisResponse(mock.getMessage(), mock.getConfidence(),
                mock.getDecision(), mock.getMatchedScenario(), mock.getRoundNumber());
    }

    private DiagnosisResponse diagnoseWithLlm(DiagnosisSession session, String userMessage,
                                              List<Message> history, Manual manual) {
        Product product = session.getProduct();
        List<KnowledgeBase> knowledge = knowledgeBaseRepository.findActiveKnowledgeForSku(product.getSku());

        StringBuilder kb = new StringBuilder();
        for (KnowledgeBase k : knowledge) {
            kb.append("- 场景: ").append(k.getScenarioName())
              .append(" | 客户症状: ").append(k.getSymptomDescription())
              .append(" | 关键词: ").append(k.getKeywords()).append("\n");
        }

        StringBuilder convo = new StringBuilder();
        int from = Math.max(0, history.size() - 10);
        for (Message m : history.subList(from, history.size())) {
            convo.append(m.getRole() == Message.MessageRole.USER ? "Customer: " : "Support: ")
                  .append(m.getContent()).append("\n");
        }

        String system = """
                You are ToolFix AI, a professional after-sales support assistant for power tools.
                You are helping a customer with product: %s (SKU: %s).

                Manual digest (ground truth, cite it when possible):
                %s

                Known false-fault scenarios for this SKU (scenario names are exact identifiers):
                %s
                Rules:
                - Reply in English, friendly and simple, at most 3 sentences.
                - If the customer's issue clearly matches a known scenario AND you can guide them: decision=FALSE_FAULT_GUIDE, matched_scenario=<exact scenario name>, and briefly confirm the likely cause before offering the guide.
                - If safety concerns exist or the issue seems like a real hardware fault after checks: decision=TRANSFER_TO_HUMAN, matched_scenario=null.
                - Otherwise ask exactly ONE focused troubleshooting question: decision=CONTINUE, matched_scenario=null.
                - You have at most %d rounds total; current round is %d. Do not stall: by round %d prefer FALSE_FAULT_GUIDE or TRANSFER_TO_HUMAN over CONTINUE.
                Respond ONLY with compact JSON (no markdown):
                {"reply":"...","confidence":0.0-1.0,"decision":"CONTINUE|FALSE_FAULT_GUIDE|TRANSFER_TO_HUMAN","matched_scenario":null}
                """.formatted(
                product.getProductName(), product.getSku(),
                manual != null && manual.getContentDigest() != null
                        ? truncate(manual.getContentDigest(), 6000) : "(manual not available)",
                kb.isEmpty() ? "(none)" : kb,
                maxConversationRounds, session.getRoundCount() + 1, maxConversationRounds);

        String raw = llmService.chatText(system, convo + "Customer: " + userMessage, 0.6, 800);
        JsonNode root = llmService.extractJson(raw);

        int round = session.getRoundCount() + 1;
        String decisionStr = root.path("decision").asText("CONTINUE").toUpperCase();
        MockAIDiagnosisService.DiagnosisDecision decision =
                switch (decisionStr) {
                    case "FALSE_FAULT_GUIDE" -> MockAIDiagnosisService.DiagnosisDecision.FALSE_FAULT_GUIDE;
                    case "TRANSFER_TO_HUMAN" -> MockAIDiagnosisService.DiagnosisDecision.TRANSFER_TO_HUMAN;
                    default -> MockAIDiagnosisService.DiagnosisDecision.CONTINUE;
                };

        String matched = root.path("matched_scenario").isNull() ? null : root.path("matched_scenario").asText(null);
        if (decision == MockAIDiagnosisService.DiagnosisDecision.FALSE_FAULT_GUIDE && matched == null) {
            decision = MockAIDiagnosisService.DiagnosisDecision.CONTINUE;
        }

        double confidence = root.path("confidence").asDouble(0.7);
        String reply = root.path("reply").asText("Could you tell me more details about the issue?");

        return new DiagnosisResponse(reply, Math.max(0, Math.min(1, confidence)),
                decision, matched, round);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n...(truncated)";
    }

    // ------- 统一输出结构 -------
    public record DiagnosisResponse(String message, double confidence,
                                    MockAIDiagnosisService.DiagnosisDecision decision,
                                    String matchedScenario, int roundNumber) {
    }
}
