package com.toolfix.repository;

import com.toolfix.domain.DiagnosisSession;
import com.toolfix.domain.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosisSessionRepository extends JpaRepository<DiagnosisSession, Long> {
    Optional<DiagnosisSession> findBySessionUuid(String sessionUuid);
    
    Page<DiagnosisSession> findByShopOrderByCreatedAtDesc(Shop shop, Pageable pageable);
    
    @Query("SELECT s FROM DiagnosisSession s WHERE s.shop = :shop " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:outcome IS NULL OR s.outcome = :outcome) " +
           "ORDER BY s.createdAt DESC")
    Page<DiagnosisSession> findByShopWithFilters(
        @Param("shop") Shop shop,
        @Param("status") DiagnosisSession.SessionStatus status,
        @Param("outcome") DiagnosisSession.SessionOutcome outcome,
        Pageable pageable
    );
    
    List<DiagnosisSession> findByTransferredToHumanTrueAndShop(Shop shop);
    
    @Query("SELECT COUNT(s) FROM DiagnosisSession s WHERE s.shop = :shop " +
           "AND s.createdAt >= :startDate")
    Long countByShopAndCreatedAtAfter(@Param("shop") Shop shop, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(s) FROM DiagnosisSession s WHERE s.shop = :shop " +
           "AND s.outcome = :outcome AND s.createdAt >= :startDate")
    Long countByShopAndOutcomeAndCreatedAtAfter(
        @Param("shop") Shop shop,
        @Param("outcome") DiagnosisSession.SessionOutcome outcome,
        @Param("startDate") LocalDateTime startDate
    );
    
    Long countByCreatedAtAfter(LocalDateTime startDate);
    
    Long countByOutcomeAndCreatedAtAfter(
        DiagnosisSession.SessionOutcome outcome,
        LocalDateTime startDate
    );
}
