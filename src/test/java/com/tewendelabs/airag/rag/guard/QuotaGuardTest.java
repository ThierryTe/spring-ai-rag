package com.tewendelabs.airag.rag.guard;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tewendelabs.airag.config.DemoSessionProperties;
import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.repository.DemoSessionRepository;

class QuotaGuardTest {

    private final DemoSessionRepository demoSessionRepository = mock(DemoSessionRepository.class);
    private final DemoSessionProperties properties = new DemoSessionProperties(5, 2, 2, 10);
    private final QuotaGuard guard = new QuotaGuard(demoSessionRepository, properties);

    @Test
    void sessionUnderQuota_consumesOneTokenAndDoesNotThrow() {
        DemoSession session = DemoSession.builder().id(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(demoSessionRepository.incrementQuestionsUsedIfUnderLimit(session.getId(), 5)).thenReturn(1);

        guard.checkAndConsume(session);

        verify(demoSessionRepository).incrementQuestionsUsedIfUnderLimit(session.getId(), 5);
    }

    @Test
    void sessionAtQuota_refusesWithoutThrowingBeforeAtomicCheck() {
        DemoSession session = DemoSession.builder().id(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(demoSessionRepository.incrementQuestionsUsedIfUnderLimit(session.getId(), 5)).thenReturn(0);

        assertThatThrownBy(() -> guard.checkAndConsume(session))
                .isInstanceOf(RagRefusalException.class)
                .extracting(ex -> ((RagRefusalException) ex).getReason())
                .isEqualTo(RefusalReason.QUOTA_EXCEEDED);
    }

    @Test
    void expiredSession_refusesAsSessionExpiredWithoutConsumingQuota() {
        DemoSession session = DemoSession.builder().id(UUID.randomUUID())
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();

        assertThatThrownBy(() -> guard.checkAndConsume(session))
                .isInstanceOf(RagRefusalException.class)
                .extracting(ex -> ((RagRefusalException) ex).getReason())
                .isEqualTo(RefusalReason.SESSION_EXPIRED);

        verify(demoSessionRepository, never()).incrementQuestionsUsedIfUnderLimit(any(), anyInt());
    }
}
