package com.tewendelabs.airag.dto;

import java.math.BigDecimal;
import java.util.List;

public record ObservabilitySummaryResponse(
        int days,
        long totalQueries,
        long allowedQueries,
        long refusedQueries,
        List<RefusalReasonCount> refusalBreakdown,
        Double avgLatencyMs,
        long totalTokensUsed,
        BigDecimal totalEstimatedCostUsd,
        List<DailyQueryCount> dailyVolume) {
}
