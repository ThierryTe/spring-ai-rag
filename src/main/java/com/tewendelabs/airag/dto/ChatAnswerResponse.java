package com.tewendelabs.airag.dto;

import java.util.List;

import com.tewendelabs.airag.rag.guard.RefusalReason;

public record ChatAnswerResponse(
        String answer,
        List<SourceCitation> sources,
        boolean refused,
        RefusalReason refusalReason,
        long latencyMs) {
}
