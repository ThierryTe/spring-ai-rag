package com.tewendelabs.airag.dto;

import java.util.UUID;

import com.tewendelabs.airag.entity.DocumentStatus;

public record DocumentUploadResponse(UUID documentId, DocumentStatus status) {
}
