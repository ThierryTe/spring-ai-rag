package com.tewendelabs.airag.repository;

import java.util.UUID;

public record ChunkSearchResult(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        String filename,
        String sectionLabel,
        int chunkIndex,
        Integer pageNumber,
        String content,
        double similarity) {
}
