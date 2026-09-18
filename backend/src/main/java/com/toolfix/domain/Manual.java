package com.toolfix.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "manuals")
@EqualsAndHashCode(callSuper = true)
public class Manual extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;
    
    @Column(nullable = false)
    private String originalFileName;
    
    @Column(nullable = false)
    private String storedFileName;
    
    @Column(nullable = false)
    private Long fileSize;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ManualStatus status = ManualStatus.UNCONFIRMED;
    
    @Column(columnDefinition = "TEXT")
    private String extractedProductName;
    
    @Column(columnDefinition = "TEXT")
    private String extractedModel;
    
    @Column(columnDefinition = "TEXT")
    private String extractedBatteryInfo;
    
    @Column(columnDefinition = "TEXT")
    private String extractedPowerInfo;
    
    @Column(columnDefinition = "TEXT")
    private String extractedCompatibleBatteries;
    
    @Column(columnDefinition = "TEXT")
    private String extractedComponentCodes;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String extractedSafetyWarnings;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String extractedWarrantyTerms;
    
    @Column(columnDefinition = "TEXT")
    private String vectorIds;
    
    private String parseJobId;
    
    @Column(columnDefinition = "TEXT")
    private String parseErrorMessage;
    
    /** AI 训练状态机: UPLOADED -> READING -> GENERATING -> DONE / FAILED */
    @Column(nullable = false)
    private String parseStatus = "UPLOADED";
    
    /** 训练进度 0-100 */
    @Column(nullable = false)
    private Integer parseProgress = 0;
    
    /** 进度/错误的人类可读信息 */
    @Column(columnDefinition = "TEXT")
    private String parseMessage;
    
    /** AI 读手册后的内容摘要（供诊断对话引用） */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String contentDigest;
    
    /** AI 生成的可能问题原始 JSON */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String possibleIssuesJson;
    
    public enum ManualStatus {
        UNCONFIRMED,
        LOCKED
    }
}
