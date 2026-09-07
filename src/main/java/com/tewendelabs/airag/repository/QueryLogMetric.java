package com.tewendelabs.airag.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;


public interface QueryLogMetric {

    boolean isAccessAllowed();

    String getRefusalReason();

    Integer getLatencyMs();

    Integer getTokensUsed();

    BigDecimal getEstimatedCostUsd();

    LocalDateTime getCreatedAt();
}
