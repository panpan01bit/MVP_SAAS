package com.toolfix.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "shops")
@EqualsAndHashCode(callSuper = true)
public class Shop extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String shopifyDomain;
    
    @Column(nullable = false)
    private String shopName;
    
    /** Shopify 访问令牌，绝不通过 API 下发 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false)
    private String accessToken;
    
    @Column(nullable = false)
    private String platform = "SHOPIFY";
    
    @Column(nullable = false)
    private Boolean active = true;
    
    private String ownerEmail;
    
    @Column(columnDefinition = "TEXT")
    private String webhookIds;
}
