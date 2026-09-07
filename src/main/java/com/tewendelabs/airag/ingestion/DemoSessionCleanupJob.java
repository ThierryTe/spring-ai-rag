package com.tewendelabs.airag.ingestion;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.repository.DemoSessionRepository;


@Component
@ConditionalOnProperty(name = "demo.session.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class DemoSessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DemoSessionCleanupJob.class);

    private final DemoSessionRepository demoSessionRepository;

    public DemoSessionCleanupJob(DemoSessionRepository demoSessionRepository) {
        this.demoSessionRepository = demoSessionRepository;
    }

    @Scheduled(fixedRateString = "${demo.session.cleanup-interval-ms:1800000}")
    public void cleanupExpiredSessions() {
        List<DemoSession> expired = demoSessionRepository.findByExpiresAtBefore(LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }
        demoSessionRepository.deleteAll(expired);
        log.info("{} session(s) demo expiree(s) nettoyee(s)", expired.size());
    }
}
