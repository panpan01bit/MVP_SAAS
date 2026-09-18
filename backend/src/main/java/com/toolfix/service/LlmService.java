package com.toolfix.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * 智谱 GLM API 客户端（OpenAI 兼容协议）。
 * 提供文本对话与视觉(图片)对话两种能力，供手册解析与 AI 诊断使用。
 * API Key 未配置时 isAvailable()=false，上层自动降级到 Mock 实现。
 */
@Service
@Slf4j
public class LlmService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String visionModel;
    private final String textModel;
    private final boolean disableThinking;
    private final boolean available;

    public LlmService(
            @Value("${toolfix.llm.base-url}") String baseUrl,
            @Value("${toolfix.llm.api-key}") String apiKey,
            @Value("${toolfix.llm.vision-model}") String visionModel,
            @Value("${toolfix.llm.text-model}") String textModel,
            @Value("${toolfix.llm.disable-thinking}") boolean disableThinking) {

        this.visionModel = visionModel;
        this.textModel = textModel;
        this.disableThinking = disableThinking;
        this.available = apiKey != null && !apiKey.isBlank();

        // 每次请求新建连接：避免复用被网关关闭的陈旧 keep-alive 连接
        // (症状: "Connection prematurely closed BEFORE response")
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.newConnection()))
                .defaultHeader("Authorization", "Bearer " + (apiKey == null ? "" : apiKey))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();

        if (available) {
            log.info("LlmService 已启用: baseUrl={}, vision={}, text={}", baseUrl, visionModel, textModel);
        } else {
            log.warn("LLM_API_KEY 未配置，AI 功能将降级为 Mock 模式");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** 文本对话（诊断回复、内容总结、JSON 生成等） */
    public String chatText(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        return chat(textModel, systemPrompt, userPrompt, List.of(), temperature, maxTokens);
    }

    /** 视觉对话：图片以 dataURI(base64) 形式传入，用于读取"转曲"手册页 */
    public String chatVision(String userPrompt, List<String> imageDataUris, int maxTokens) {
        return chat(visionModel, null, userPrompt, imageDataUris, 0.4, maxTokens);
    }

    private String chat(String model, String systemPrompt, String userPrompt,
                        List<String> imageDataUris, double temperature, int maxTokens) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            if (disableThinking) {
                body.putObject("thinking").put("type", "disabled");
            }

            ArrayNode messages = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.addObject().put("role", "system").put("content", systemPrompt);
            }
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            if (imageDataUris.isEmpty()) {
                user.put("content", userPrompt);
            } else {
                ArrayNode parts = user.putArray("content");
                for (String uri : imageDataUris) {
                    parts.addObject()
                            .put("type", "image_url")
                            .putObject("image_url").put("url", uri);
                }
                parts.addObject().put("type", "text").put("text", userPrompt);
            }

            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(150))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(3))
                            .filter(this::isRetryable)
                            .transientErrors(true))
                    .block(Duration.ofSeconds(600));

            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.at("/choices/0/message/content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("LLM 返回空内容: " + response.substring(0, Math.min(300, response.length())));
            }
            return content.asText();

        } catch (Exception e) {
            log.error("LLM 调用失败 (model={}): {}", model, e.getMessage());
            throw new LlmException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /** 网络抖动/网关闪断/5xx 时重试；业务性错误(如JSON解析)不重试 */
    private boolean isRetryable(Throwable t) {
        return t instanceof IOException || t instanceof TimeoutException
                || t.getCause() instanceof IOException;
    }

    /** 从 LLM 回复中提取 JSON（容忍 markdown 代码块包裹） */
    public JsonNode extractJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            throw new LlmException("LLM 返回的 JSON 无法解析: " + e.getMessage(), e);
        }
    }

    public static class LlmException extends RuntimeException {
        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
