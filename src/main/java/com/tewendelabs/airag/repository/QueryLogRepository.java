package com.tewendelabs.airag.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tewendelabs.airag.entity.QueryLog;

@Repository
public interface QueryLogRepository extends JpaRepository<QueryLog, UUID> {

    List<QueryLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Alimente le dashboard admin (AdminObservabilityService) : trafic demo et authentifie
     * confondus (userId et demoSessionId ne sont pas selectionnes ici, l'agregation ne distingue
     * pas les deux - voir le plan de conception, decision de scope volontaire).
     */
    @Query("SELECT q.accessAllowed AS accessAllowed, q.refusalReason AS refusalReason, "
            + "q.latencyMs AS latencyMs, q.tokensUsed AS tokensUsed, q.estimatedCostUsd AS estimatedCostUsd, "
            + "q.createdAt AS createdAt FROM QueryLog q WHERE q.createdAt >= :since")
    List<QueryLogMetric> findMetricsSince(@Param("since") LocalDateTime since);
}
