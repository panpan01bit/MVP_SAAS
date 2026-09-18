package com.toolfix.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "diagnosis_sessions")
@EqualsAndHashCode(callSuper = true)
public class DiagnosisSession extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String sessionUuid;
    
    /** HMAC 访问令牌，绝不通过 API 下发 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false)
    private String secureToken;
    
    @Column(nullable = false)
    private LocalDateTime expiryTime;
    
    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;
    
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @Column(nullable = false)
    private String shopifyOrderId;
    
    @Column(nullable = false)
    private String customerEmail;
    
    private String customerName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.IN_PROGRESS;
    
    @Enumerated(EnumType.STRING)
    private SessionOutcome outcome;
    
    @Column(nullable = false)
    private Integer roundCount = 0;
    
    private Double finalConfidence;
    
    @Column(columnDefinition = "TEXT")
    private String diagnosisSummary;
    
    @Column(columnDefinition = "TEXT")
    private String suspectedIssue;
    
    @Column(columnDefinition = "TEXT")
    private String excludedIssues;
    
    private Boolean hazardDetected = false;
    
    private String hazardKeywords;
    
    private Boolean thumbsUp;
    
    private Boolean transferredToHuman = false;
    
    @Column(columnDefinition = "TEXT")
    private String transferReason;
    
    private LocalDateTime transferredAt;
    
    private Boolean resolved = false;
    
    private String guidePageUrl;
    
    public enum SessionStatus {
        IN_PROGRESS,
        AWAITING_FEEDBACK,
        TRANSFERRED,
        RESOLVED,
        CLOSED
    }
    
    public enum SessionOutcome {
        FALSE_FAULT_INTERCEPTED,
        TRANSFERRED_TO_HUMAN,
        LOW_CONFIDENCE,
        MAX_ROUNDS_REACHED,
        NEGATIVE_FEEDBACK,
        HAZARD_DETECTED
    }
}
