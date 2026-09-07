package com.tewendelabs.airag.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DemoSessionResponse(
        UUID sessionId,
        LocalDateTime expiresAt,
        int maxQuestions,
        int maxDocuments,
        int questionsUsed,
        int documentsUsed) {
}
