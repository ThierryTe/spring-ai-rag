package com.tewendelabs.airag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tewendelabs.airag.config.DemoSessionProperties;
import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.exceptions.DemoQuotaExceededException;
import com.tewendelabs.airag.exceptions.InvalidDemoSessionException;
import com.tewendelabs.airag.repository.DemoSessionRepository;

class DemoSessionServiceTest {

    private final DemoSessionRepository demoSessionRepository = mock(DemoSessionRepository.class);
    private final DemoSessionProperties properties = new DemoSessionProperties(5, 2, 2);
    private final DemoSessionService service = new DemoSessionService(demoSessionRepository, properties);

    @Test
    void create_buildsSessionWithExpiryFromProperties() {
        when(demoSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DemoSession session = service.create("127.0.0.1");

        assertThat(session.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(session.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(119));
    }

    @Test
    void findExisting_unknownId_throwsInvalidDemoSession() {
        UUID id = UUID.randomUUID();
        when(demoSessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findExisting(id)).isInstanceOf(InvalidDemoSessionException.class);
    }

    @Test
    void findExisting_expiredSession_doesNotThrow() {
        UUID id = UUID.randomUUID();
        DemoSession expired = DemoSession.builder().id(id).expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(demoSessionRepository.findById(id)).thenReturn(Optional.of(expired));

        assertThat(service.findExisting(id)).isEqualTo(expired);
    }

    @Test
    void requireValid_expiredSession_throwsInvalidDemoSession() {
        UUID id = UUID.randomUUID();
        DemoSession expired = DemoSession.builder().id(id).expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(demoSessionRepository.findById(id)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.requireValid(id)).isInstanceOf(InvalidDemoSessionException.class);
    }

    @Test
    void requireValid_validSession_returnsIt() {
        UUID id = UUID.randomUUID();
        DemoSession session = DemoSession.builder().id(id).expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(demoSessionRepository.findById(id)).thenReturn(Optional.of(session));

        assertThat(service.requireValid(id)).isEqualTo(session);
    }

    @Test
    void consumeDocumentSlot_underQuota_doesNotThrow() {
        UUID id = UUID.randomUUID();
        when(demoSessionRepository.incrementDocumentsUsedIfUnderLimit(id, 2)).thenReturn(1);

        service.consumeDocumentSlot(id);
    }

    @Test
    void consumeDocumentSlot_atQuota_throwsDemoQuotaExceeded() {
        UUID id = UUID.randomUUID();
        when(demoSessionRepository.incrementDocumentsUsedIfUnderLimit(id, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.consumeDocumentSlot(id)).isInstanceOf(DemoQuotaExceededException.class);
    }
}
