package com.tewendelabs.airag.dto;

import java.time.Instant;
import java.util.UUID;

public record DemoSessionResponse(
        UUID sessionId,
        Instant expiresAt,
        int maxQuestions,
        int maxDocuments,
        int questionsUsed,
        int documentsUsed) {
}
