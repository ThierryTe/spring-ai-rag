package com.tewendelabs.airag.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tewendelabs.airag.config.DemoSessionProperties;
import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.exceptions.DemoQuotaExceededException;
import com.tewendelabs.airag.exceptions.InvalidDemoSessionException;
import com.tewendelabs.airag.repository.DemoSessionRepository;

@Service
public class DemoSessionService {

    private final DemoSessionRepository demoSessionRepository;
    private final DemoSessionProperties demoSessionProperties;

    public DemoSessionService(DemoSessionRepository demoSessionRepository,
            DemoSessionProperties demoSessionProperties) {
        this.demoSessionRepository = demoSessionRepository;
        this.demoSessionProperties = demoSessionProperties;
    }

    public DemoSession create(String ipAddress) {
        DemoSession session = DemoSession.builder()
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusHours(demoSessionProperties.ttlHours()))
                .build();
        return demoSessionRepository.save(session);
    }

    /**
     * Verifie uniquement l'existence : ne rejette PAS une session expiree. Utilise par le flux
     * chat, ou l'expiration doit etre traitee comme un refus RAG gracieux (SESSION_EXPIRED) par
     * {@code QuotaGuard}, pas comme une erreur 401.
     */
    public DemoSession findExisting(UUID demoSessionId) {
        return demoSessionRepository.findById(demoSessionId)
                .orElseThrow(() -> new InvalidDemoSessionException("Session demo introuvable"));
    }

    /**
     * Utilise par les endpoints non-chat (infos de session, documents) : une session expiree y
     * est simplement une requete non authentifiee, sans notion de reponse degradee.
     */
    public DemoSession requireValid(UUID demoSessionId) {
        DemoSession session = findExisting(demoSessionId);
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidDemoSessionException("Session demo expiree");
        }
        return session;
    }

    @Transactional
    public void consumeDocumentSlot(UUID demoSessionId) {
        int updated = demoSessionRepository.incrementDocumentsUsedIfUnderLimit(demoSessionId,
                demoSessionProperties.maxDocuments());
        if (updated == 0) {
            throw new DemoQuotaExceededException("Quota de documents atteint pour cette session demo");
        }
    }
}
