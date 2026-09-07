package com.tewendelabs.airag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tewendelabs.airag.dto.ObservabilitySummaryResponse;
import com.tewendelabs.airag.entity.Role;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.exceptions.ForbiddenOperationException;
import com.tewendelabs.airag.repository.QueryLogMetric;
import com.tewendelabs.airag.repository.QueryLogRepository;
import com.tewendelabs.airag.repository.UserRepository;

class AdminObservabilityServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final QueryLogRepository queryLogRepository = mock(QueryLogRepository.class);
    private final AdminObservabilityService service =
            new AdminObservabilityService(userRepository, queryLogRepository);

    @Test
    void employeeIsRejectedWithForbidden() {
        User employee = userWithRole(Role.EMPLOYEE);
        when(userRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.getSummary(employee.getId(), 30))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void adminGetsAggregatedSummary() {
        User admin = userWithRole(Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        LocalDate today = LocalDate.now();
        List<QueryLogMetric> metrics = List.of(
                metric(true, null, 100, 70, "0.090000", today.atTime(9, 0)),
                metric(true, null, 200, 70, "0.090000", today.atTime(10, 0)),
                metric(false, "SENSITIVE_TOPIC", 5, null, null, today.atTime(11, 0)),
                metric(false, "DEPARTMENT_FORBIDDEN", 5, null, null, today.minusDays(1).atTime(9, 0)));
        when(queryLogRepository.findMetricsSince(any())).thenReturn(metrics);

        ObservabilitySummaryResponse summary = service.getSummary(admin.getId(), 30);

        assertThat(summary.totalQueries()).isEqualTo(4);
        assertThat(summary.allowedQueries()).isEqualTo(2);
        assertThat(summary.refusedQueries()).isEqualTo(2);
        assertThat(summary.refusalBreakdown()).hasSize(2);
        assertThat(summary.avgLatencyMs()).isEqualTo((100 + 200 + 5 + 5) / 4.0);
        assertThat(summary.totalTokensUsed()).isEqualTo(140);
        assertThat(summary.totalEstimatedCostUsd()).isEqualByComparingTo(new BigDecimal("0.180000"));
        assertThat(summary.dailyVolume()).hasSize(2);
        assertThat(summary.dailyVolume().get(0).day()).isEqualTo(today.minusDays(1));
        assertThat(summary.dailyVolume().get(0).count()).isEqualTo(1);
        assertThat(summary.dailyVolume().get(1).day()).isEqualTo(today);
        assertThat(summary.dailyVolume().get(1).count()).isEqualTo(3);
    }

    private static User userWithRole(String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        return User.builder().id(UUID.randomUUID()).role(role).build();
    }

    private static QueryLogMetric metric(boolean accessAllowed, String refusalReason, Integer latencyMs,
            Integer tokensUsed, String estimatedCostUsd, LocalDateTime createdAt) {
        return new QueryLogMetric() {
            @Override
            public boolean isAccessAllowed() {
                return accessAllowed;
            }

            @Override
            public String getRefusalReason() {
                return refusalReason;
            }

            @Override
            public Integer getLatencyMs() {
                return latencyMs;
            }

            @Override
            public Integer getTokensUsed() {
                return tokensUsed;
            }

            @Override
            public BigDecimal getEstimatedCostUsd() {
                return estimatedCostUsd != null ? new BigDecimal(estimatedCostUsd) : null;
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return createdAt;
            }
        };
    }
}
