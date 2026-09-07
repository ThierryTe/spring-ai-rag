package com.tewendelabs.airag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingestion.chunking")
public record ChunkingProperties(int chunkSize, int chunkOverlap) {
}
