package com.toolfix.controller;

import com.toolfix.domain.*;
import com.toolfix.dto.ApiResponse;
import com.toolfix.repository.*;
import com.toolfix.service.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/diagnosis")
@RequiredArgsConstructor
@Slf4j
public class DiagnosisController {
    
    private final DiagnosisSessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final ManualRepository manualRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SecurityService securityService;
    private final HazardDetectionService hazardDetectionService;
    private final AiDiagnosisService aiDiagnosisService;
    private final NotificationService notificationService;
    
    @Value("${toolfix.storage.upload-dir}")
    private String uploadDir;
    
    @Value("${toolfix.ai.max-conversation-rounds}")
    private int maxConversationRounds;
    
    @Value("${toolfix.links.h5-base-url}")
    private String h5BaseUrl;
    
    @PostMapping("/create-session")
    public ApiResponse<SessionCreationResponse> createSession(@RequestBody CreateSessionRequest request) {
        Shop shop = shopRepository.findById(request.getShopId())
            .orElseThrow(() -> new RuntimeException("Shop not found"));
        
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new RuntimeException("Product not found"));
        
        String sessionUuid = securityService.generateSessionUuid();
        LocalDateTime expiryTime = securityService.calculateExpiryTime();
        String token = securityService.generateHmacToken(sessionUuid, expiryTime);
        
        DiagnosisSession session = new DiagnosisSession();
        session.setSessionUuid(sessionUuid);
        session.setSecureToken(token);
        session.setExpiryTime(expiryTime);
        session.setShop(shop);
        session.setProduct(product);
        session.setShopifyOrderId(request.getOrderId());
        session.setCustomerEmail(request.getCustomerEmail());
        session.setCustomerName(request.getCustomerName());
        session.setStatus(DiagnosisSession.SessionStatus.IN_PROGRESS);
        session.setRoundCount(0);
        
        session = sessionRepository.save(session);
        
        String secureLink = securityService.buildSecureLink(h5BaseUrl, sessionUuid, token);
        
        log.info("Diagnosis session created: {} for order: {}", sessionUuid, request.getOrderId());
        
        return ApiResponse.success(new SessionCreationResponse(session.getId(), sessionUuid, secureLink, expiryTime));
    }
    
    /**
     * H5 一键体验：自动创建绑定演示产品的会话，返回带 token 的链接参数。
     * 公开接口 —— 无需真实订单。
     */
    @PostMapping("/demo-session")
    public ApiResponse<DemoSessionResponse> createDemoSession() {
        List<Product> products = productRepository.findAll();
        Product product = products.stream()
                .filter(p -> Boolean.TRUE.equals(p.getHasManual()))
                .findFirst()
                .orElse(products.stream().findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No demo product seeded yet")));
        Shop shop = product.getShop();
        
        String sessionUuid = securityService.generateSessionUuid();
        LocalDateTime expiryTime = securityService.calculateExpiryTime();
        String token = securityService.generateHmacToken(sessionUuid, expiryTime);
        
        DiagnosisSession session = new DiagnosisSession();
        session.setSessionUuid(sessionUuid);
        session.setSecureToken(token);
        session.setExpiryTime(expiryTime);
        session.setShop(shop);
        session.setProduct(product);
        session.setShopifyOrderId("DEMO-" + System.currentTimeMillis());
        session.setCustomerEmail("demo-customer@example.com");
        session.setCustomerName("Demo Customer");
        session.setStatus(DiagnosisSession.SessionStatus.IN_PROGRESS);
        session.setRoundCount(0);
        session = sessionRepository.save(session);
        
        log.info("Demo session created: {} for product: {}", sessionUuid, product.getSku());
        return ApiResponse.success(new DemoSessionResponse(sessionUuid, token,
                securityService.buildSecureLink(h5BaseUrl, sessionUuid, token),
                product.getProductName()));
    }
    
    @Data
    @AllArgsConstructor
    public static class DemoSessionResponse {
        private String sessionUuid;
        private String token;
        private String secureLink;
        private String productName;
    }
    
    @GetMapping("/{sessionUuid}/validate")
    public ApiResponse<SessionValidationResponse> validateSession(
            @PathVariable String sessionUuid,
            @RequestParam String token) {
        
        DiagnosisSession session = sessionRepository.findBySessionUuid(sessionUuid)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        
        if (!securityService.validateHmacToken(sessionUuid, session.getExpiryTime(), token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or tampered link");
        }
        
        if (securityService.isExpired(session.getExpiryTime())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Link has expired");
        }
        
        SessionValidationResponse response = new SessionValidationResponse();
        response.setValid(true);
        response.setSessionId(session.getId());
        response.setProductName(session.getProduct().getProductName());
        response.setStatus(session.getStatus().name());
        response.setRoundCount(session.getRoundCount());
        response.setMaxRounds(maxConversationRounds);
        
        return ApiResponse.success(response);
    }
    
    @PostMapping("/{sessionUuid}/chat")
    public ApiResponse<ChatResponse> chat(
            @PathVariable String sessionUuid,
            @RequestParam String token,
            @RequestParam String message,
            @RequestParam(required = false) List<MultipartFile> images) {
        
        DiagnosisSession session = sessionRepository.findBySessionUuid(sessionUuid)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        
        if (!securityService.validateHmacToken(sessionUuid, session.getExpiryTime(), token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid token");
        }
        
        if (securityService.isExpired(session.getExpiryTime())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Link has expired");
        }
        
        // 已转人工/已解决的会话不再进 AI 流程，避免与人工回复撞车
        if (session.getStatus() == DiagnosisSession.SessionStatus.TRANSFERRED
                || session.getStatus() == DiagnosisSession.SessionStatus.RESOLVED
                || session.getStatus() == DiagnosisSession.SessionStatus.CLOSED) {
            return ApiResponse.error("This session has been " + session.getStatus().name().toLowerCase()
                    + ". Our support team will follow up.");
        }
        
        if (session.getRoundCount() >= maxConversationRounds) {
            return ApiResponse.error("Maximum conversation rounds reached. Transferring to human support.");
        }
        
        HazardDetectionService.HazardDetectionResult hazardResult = hazardDetectionService.detectHazards(message);
        
        if (hazardResult.isDetected()) {
            return handleHazardDetection(session, message, hazardResult, images);
        }
        
        List<String> imageUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            imageUrls = saveImages(images);
        }
        
        Message userMessage = new Message();
        userMessage.setSession(session);
        userMessage.setRole(Message.MessageRole.USER);
        userMessage.setContent(message);
        userMessage.setRoundNumber(session.getRoundCount() + 1);
        if (!imageUrls.isEmpty()) {
            userMessage.setImageUrls(String.join(",", imageUrls));
        }
        messageRepository.save(userMessage);
        
        List<Message> conversationHistory = messageRepository.findBySessionOrderByCreatedAtAsc(session);
        
        Manual manual = manualRepository.findByProduct(session.getProduct()).orElse(null);
        
        AiDiagnosisService.DiagnosisResponse aiResponse = aiDiagnosisService.diagnose(
            session, message, conversationHistory, manual);
        
        session.setRoundCount(aiResponse.roundNumber());
        session.setFinalConfidence(aiResponse.confidence());
        
        Message assistantMessage = new Message();
        assistantMessage.setSession(session);
        assistantMessage.setRole(Message.MessageRole.ASSISTANT);
        assistantMessage.setContent(aiResponse.message());
        assistantMessage.setRoundNumber(aiResponse.roundNumber());
        messageRepository.save(assistantMessage);
        
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setMessage(aiResponse.message());
        chatResponse.setRoundNumber(aiResponse.roundNumber());
        chatResponse.setConfidence(aiResponse.confidence());
        chatResponse.setMaxRoundsReached(aiResponse.roundNumber() >= maxConversationRounds);
        
        if (aiResponse.decision() == MockAIDiagnosisService.DiagnosisDecision.FALSE_FAULT_GUIDE) {
            KnowledgeBase knowledge = knowledgeBaseRepository.findActiveKnowledgeForSku(session.getProduct().getSku())
                .stream()
                .filter(kb -> kb.getScenarioName().equalsIgnoreCase(aiResponse.matchedScenario()))
                .findFirst()
                .orElse(null);
            
            if (knowledge != null) {
                String guideUrl = h5BaseUrl + "/guide/" + knowledge.getGuidePageSlug();
                session.setGuidePageUrl(guideUrl);
                session.setStatus(DiagnosisSession.SessionStatus.AWAITING_FEEDBACK);
                session.setOutcome(DiagnosisSession.SessionOutcome.FALSE_FAULT_INTERCEPTED);
                
                chatResponse.setGuideUrl(guideUrl);
                chatResponse.setNeedsTransfer(false);
            } else {
                // 知识库未命中：回退为转人工，避免客户拿不到指南后卡死
                log.warn("FALSE_FAULT_GUIDE matched scenario '{}' but no KB entry for SKU {}", 
                        aiResponse.matchedScenario(), session.getProduct().getSku());
                handleTransfer(session, "Guide not available for matched scenario", false);
                chatResponse.setNeedsTransfer(true);
            }
        } else if (aiResponse.decision() == MockAIDiagnosisService.DiagnosisDecision.TRANSFER_TO_HUMAN) {
            handleTransfer(session, "AI diagnosis recommends human support", false);
            chatResponse.setNeedsTransfer(true);
        }
        
        sessionRepository.save(session);
        
        return ApiResponse.success(chatResponse);
    }
    
    private ApiResponse<ChatResponse> handleHazardDetection(
            DiagnosisSession session,
            String userMessage,
            HazardDetectionService.HazardDetectionResult hazardResult,
            List<MultipartFile> images) {
        
        log.warn("HAZARD DETECTED in session {}: {}", session.getSessionUuid(), hazardResult.getKeyword().getKeyword());
        
        List<String> imageUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            imageUrls = saveImages(images);
        }
        
        Message userMsg = new Message();
        userMsg.setSession(session);
        userMsg.setRole(Message.MessageRole.USER);
        userMsg.setContent(userMessage);
        userMsg.setRoundNumber(session.getRoundCount() + 1);
        if (!imageUrls.isEmpty()) {
            userMsg.setImageUrls(String.join(",", imageUrls));
        }
        messageRepository.save(userMsg);
        
        Message assistantMsg = new Message();
        assistantMsg.setSession(session);
        assistantMsg.setRole(Message.MessageRole.ASSISTANT);
        assistantMsg.setContent(hazardResult.getSafetyResponse());
        assistantMsg.setRoundNumber(session.getRoundCount() + 1);
        assistantMsg.setIsHazardWarning(true);
        messageRepository.save(assistantMsg);
        
        session.setHazardDetected(true);
        session.setHazardKeywords(hazardResult.getKeyword().getKeyword());
        handleTransfer(session, "Hazard detected: " + hazardResult.getKeyword().getKeyword(), true);
        
        sessionRepository.save(session);
        
        ChatResponse response = new ChatResponse();
        response.setMessage(hazardResult.getSafetyResponse());
        response.setRoundNumber(session.getRoundCount() + 1);
        response.setConfidence(1.0);
        response.setNeedsTransfer(true);
        response.setHazardDetected(true);
        
        return ApiResponse.success(response);
    }
    
    private void handleTransfer(DiagnosisSession session, String reason, boolean isHazard) {
        session.setTransferredToHuman(true);
        session.setTransferReason(reason);
        session.setTransferredAt(LocalDateTime.now());
        session.setStatus(DiagnosisSession.SessionStatus.TRANSFERRED);
        
        if (isHazard) {
            session.setOutcome(DiagnosisSession.SessionOutcome.HAZARD_DETECTED);
        } else if (session.getOutcome() == null) {
            session.setOutcome(DiagnosisSession.SessionOutcome.TRANSFERRED_TO_HUMAN);
        }
        
        notificationService.sendTransferNotification(session, isHazard);
    }
    
    private List<String> saveImages(List<MultipartFile> images) {
        List<String> imageUrls = new ArrayList<>();
        
        try {
            Files.createDirectories(Paths.get(uploadDir, "images"));
            
            for (MultipartFile image : images) {
                if (!image.isEmpty()) {
                    String fileName = UUID.randomUUID().toString() + "_" + sanitizeFileName(image.getOriginalFilename());
                    Path filePath = Paths.get(uploadDir, "images", fileName);
                    image.transferTo(filePath.toFile());
                    imageUrls.add("/uploads/images/" + fileName);
                }
            }
        } catch (IOException e) {
            log.error("Failed to save images", e);
        }
        
        return imageUrls;
    }
    
    /** 清洗上传文件名，防路径穿越（去掉目录部分与危险字符） */
    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) return "upload";
        String name = Paths.get(original).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    @PostMapping("/{sessionUuid}/feedback")
    public ApiResponse<Void> submitFeedback(
            @PathVariable String sessionUuid,
            @RequestParam String token,
            @RequestBody FeedbackRequest request) {
        
        DiagnosisSession session = sessionRepository.findBySessionUuid(sessionUuid)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        
        if (!securityService.validateHmacToken(sessionUuid, session.getExpiryTime(), token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid token");
        }
        
        session.setThumbsUp(request.isThumbsUp());
        
        if (!request.isThumbsUp()) {
            handleTransfer(session, "Negative feedback from customer", false);
            session.setOutcome(DiagnosisSession.SessionOutcome.NEGATIVE_FEEDBACK);
        }
        
        sessionRepository.save(session);
        
        return ApiResponse.success("Feedback recorded", null);
    }
    
    @PostMapping("/{sessionUuid}/resolve")
    public ApiResponse<Void> markResolved(
            @PathVariable String sessionUuid,
            @RequestParam String token,
            @RequestBody ResolveRequest request) {
        
        DiagnosisSession session = sessionRepository.findBySessionUuid(sessionUuid)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        
        if (!securityService.validateHmacToken(sessionUuid, session.getExpiryTime(), token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid token");
        }
        
        session.setResolved(request.isResolved());
        
        if (request.isResolved()) {
            session.setStatus(DiagnosisSession.SessionStatus.RESOLVED);
        } else {
            handleTransfer(session, "User reported issue not resolved", false);
        }
        
        sessionRepository.save(session);
        
        return ApiResponse.success("Status updated", null);
    }
    
    @Data
    public static class CreateSessionRequest {
        private Long shopId;
        private Long productId;
        private String orderId;
        private String customerEmail;
        private String customerName;
    }
    
    @Data
    @AllArgsConstructor
    public static class SessionCreationResponse {
        private Long sessionId;
        private String sessionUuid;
        private String secureLink;
        private LocalDateTime expiryTime;
    }
    
    @Data
    public static class SessionValidationResponse {
        private boolean valid;
        private Long sessionId;
        private String productName;
        private String status;
        private int roundCount;
        private int maxRounds;
    }
    
    @Data
    public static class ChatResponse {
        private String message;
        private int roundNumber;
        private double confidence;
        private boolean maxRoundsReached;
        private boolean needsTransfer;
        private boolean hazardDetected;
        private String guideUrl;
    }
    
    @Data
    public static class FeedbackRequest {
        private boolean thumbsUp;
    }
    
    @Data
    public static class ResolveRequest {
        private boolean resolved;
    }
}
