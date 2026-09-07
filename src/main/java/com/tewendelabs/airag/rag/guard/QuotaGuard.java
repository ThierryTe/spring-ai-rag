package com.tewendelabs.airag.rag.guard;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tewendelabs.airag.config.DemoSessionProperties;
import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.repository.DemoSessionRepository;


@Component
public class QuotaGuard {

    private final DemoSessionRepository demoSessionRepository;
    private final DemoSessionProperties demoSessionProperties;

    public QuotaGuard(DemoSessionRepository demoSessionRepository, DemoSessionProperties demoSessionProperties) {
        this.demoSessionRepository = demoSessionRepository;
        this.demoSessionProperties = demoSessionProperties;
    }

    @Transactional
    public void checkAndConsume(DemoSession session) {
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RagRefusalException(RefusalReason.SESSION_EXPIRED);
        }
        int updated = demoSessionRepository.incrementQuestionsUsedIfUnderLimit(session.getId(),
                demoSessionProperties.maxQuestions());
        if (updated == 0) {
            throw new RagRefusalException(RefusalReason.QUOTA_EXCEEDED);
        }
    }
}
