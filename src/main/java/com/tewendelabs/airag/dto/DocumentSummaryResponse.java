package com.tewendelabs.airag.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.tewendelabs.airag.entity.DocumentStatus;

public record DocumentSummaryResponse(
        UUID id,
        String filename,
        String title,
        DocumentStatus status,
        Integer pageCount,
        LocalDateTime ingestedAt) {
}
