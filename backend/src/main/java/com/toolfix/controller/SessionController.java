package com.toolfix.controller;

import com.toolfix.domain.DiagnosisSession;
import com.toolfix.domain.Message;
import com.toolfix.domain.Shop;
import com.toolfix.dto.ApiResponse;
import com.toolfix.repository.DiagnosisSessionRepository;
import com.toolfix.repository.MessageRepository;
import com.toolfix.repository.ShopRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {
    
    private final DiagnosisSessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ShopRepository shopRepository;
    
    @GetMapping
    public ApiResponse<Page<DiagnosisSession>> getSessions(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<DiagnosisSession> sessions;
        
        if (shopId != null) {
            Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));
            
            DiagnosisSession.SessionStatus statusEnum = status != null ? 
                DiagnosisSession.SessionStatus.valueOf(status) : null;
            DiagnosisSession.SessionOutcome outcomeEnum = outcome != null ? 
                DiagnosisSession.SessionOutcome.valueOf(outcome) : null;
            
            sessions = sessionRepository.findByShopWithFilters(shop, statusEnum, outcomeEnum, pageable);
        } else {
            sessions = sessionRepository.findAll(pageable);
        }
        
        return ApiResponse.success(sessions);
    }
    
    @GetMapping("/{id}")
    public ApiResponse<SessionDetailResponse> getSessionDetail(@PathVariable Long id) {
        DiagnosisSession session = sessionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(id);
        
        SessionDetailResponse response = new SessionDetailResponse();
        response.setSession(session);
        response.setMessages(messages);
        
        return ApiResponse.success(response);
    }
    
    @GetMapping("/uuid/{sessionUuid}")
    public ApiResponse<SessionDetailResponse> getSessionByUuid(@PathVariable String sessionUuid) {
        DiagnosisSession session = sessionRepository.findBySessionUuid(sessionUuid)
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        
        SessionDetailResponse response = new SessionDetailResponse();
        response.setSession(session);
        response.setMessages(messages);
        
        return ApiResponse.success(response);
    }
    
    @GetMapping("/transferred")
    public ApiResponse<List<DiagnosisSession>> getTransferredSessions(@RequestParam Long shopId) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new RuntimeException("Shop not found"));
        
        List<DiagnosisSession> sessions = sessionRepository.findByTransferredToHumanTrueAndShop(shop);
        return ApiResponse.success(sessions);
    }
    
    @PostMapping("/{id}/human-reply")
    public ApiResponse<Message> sendHumanReply(@PathVariable Long id, @RequestBody HumanReplyRequest request) {
        DiagnosisSession session = sessionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        Message message = new Message();
        message.setSession(session);
        message.setRole(Message.MessageRole.ASSISTANT);
        message.setContent(request.getMessage());
        message.setRoundNumber(session.getRoundCount() + 1);
        message.setIsFromHuman(true);
        
        message = messageRepository.save(message);
        
        log.info("Human reply sent for session: {}", id);
        
        return ApiResponse.success("Human reply sent successfully", message);
    }
    
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(@RequestParam(required = false) Long shopId) {
        Map<String, Object> stats = new HashMap<>();
        
        Shop shop = null;
        if (shopId != null) {
            shop = shopRepository.findById(shopId).orElse(null);
        }
        
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        
        if (shop != null) {
            long totalSessions = sessionRepository.countByShopAndCreatedAtAfter(shop, last30Days);
            long intercepted = sessionRepository.countByShopAndOutcomeAndCreatedAtAfter(
                shop, DiagnosisSession.SessionOutcome.FALSE_FAULT_INTERCEPTED, last30Days);
            long transferred = sessionRepository.countByShopAndOutcomeAndCreatedAtAfter(
                shop, DiagnosisSession.SessionOutcome.TRANSFERRED_TO_HUMAN, last30Days);
            long hazards = sessionRepository.countByShopAndOutcomeAndCreatedAtAfter(
                shop, DiagnosisSession.SessionOutcome.HAZARD_DETECTED, last30Days);
            
            stats.put("totalSessions", totalSessions);
            stats.put("interceptedCount", intercepted);
            stats.put("transferredCount", transferred);
            stats.put("hazardCount", hazards);
            stats.put("interceptRate", totalSessions > 0 ? (double) intercepted / totalSessions * 100 : 0);
        } else {
            // 全店铺统计（不限定 shopId）
            long totalSessions = sessionRepository.countByCreatedAtAfter(last30Days);
            long intercepted = sessionRepository.countByOutcomeAndCreatedAtAfter(
                DiagnosisSession.SessionOutcome.FALSE_FAULT_INTERCEPTED, last30Days);
            long transferred = sessionRepository.countByOutcomeAndCreatedAtAfter(
                DiagnosisSession.SessionOutcome.TRANSFERRED_TO_HUMAN, last30Days);
            long hazards = sessionRepository.countByOutcomeAndCreatedAtAfter(
                DiagnosisSession.SessionOutcome.HAZARD_DETECTED, last30Days);
            
            stats.put("totalSessions", totalSessions);
            stats.put("interceptedCount", intercepted);
            stats.put("transferredCount", transferred);
            stats.put("hazardCount", hazards);
            stats.put("interceptRate", totalSessions > 0 ? (double) intercepted / totalSessions * 100 : 0);
        }
        
        return ApiResponse.success(stats);
    }
    
    @Data
    public static class SessionDetailResponse {
        private DiagnosisSession session;
        private List<Message> messages;
    }
    
    @Data
    public static class HumanReplyRequest {
        private String message;
    }
}
