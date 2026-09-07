package com.tewendelabs.airag.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.tewendelabs.airag.dto.DailyQueryCount;
import com.tewendelabs.airag.dto.ObservabilitySummaryResponse;
import com.tewendelabs.airag.dto.RefusalReasonCount;
import com.tewendelabs.airag.entity.Role;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.exceptions.ForbiddenOperationException;
import com.tewendelabs.airag.exceptions.ResourceNotFoundException;
import com.tewendelabs.airag.repository.QueryLogMetric;
import com.tewendelabs.airag.repository.QueryLogRepository;
import com.tewendelabs.airag.repository.UserRepository;


@Service
public class AdminObservabilityService {

    private final UserRepository userRepository;
    private final QueryLogRepository queryLogRepository;

    public AdminObservabilityService(UserRepository userRepository, QueryLogRepository queryLogRepository) {
        this.userRepository = userRepository;
        this.queryLogRepository = queryLogRepository;
    }

    public ObservabilitySummaryResponse getSummary(UUID userId, int days) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        requireAdminRole(user);

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<QueryLogMetric> metrics = queryLogRepository.findMetricsSince(since);

        long allowed = metrics.stream().filter(QueryLogMetric::isAccessAllowed).count();
        long refused = metrics.size() - allowed;

        List<RefusalReasonCount> refusalBreakdown = metrics.stream()
                .filter(m -> !m.isAccessAllowed() && m.getRefusalReason() != null)
                .collect(Collectors.groupingBy(QueryLogMetric::getRefusalReason, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new RefusalReasonCount(e.getKey(), e.getValue()))
                .toList();

        java.util.OptionalDouble averageLatency = metrics.stream()
                .map(QueryLogMetric::getLatencyMs)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
        Double avgLatencyMs = averageLatency.isPresent() ? averageLatency.getAsDouble() : null;

        long totalTokensUsed = metrics.stream()
                .map(QueryLogMetric::getTokensUsed)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        BigDecimal totalEstimatedCostUsd = metrics.stream()
                .map(QueryLogMetric::getEstimatedCostUsd)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<java.time.LocalDate, Long> perDay = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getCreatedAt().toLocalDate(), TreeMap::new,
                        Collectors.counting()));
        List<DailyQueryCount> dailyVolume = perDay.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> new DailyQueryCount(e.getKey(), e.getValue()))
                .toList();

        return new ObservabilitySummaryResponse(days, metrics.size(), allowed, refused, refusalBreakdown,
                avgLatencyMs, totalTokensUsed, totalEstimatedCostUsd, dailyVolume);
    }

    private void requireAdminRole(User user) {
        if (!Role.ADMIN.equals(user.getRole().getCode())) {
            throw new ForbiddenOperationException("Role insuffisant pour cette operation");
        }
    }
}
