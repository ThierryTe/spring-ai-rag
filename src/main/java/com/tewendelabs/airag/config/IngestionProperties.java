package com.tewendelabs.airag.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(long maxFileSizeMb, int maxPages, List<String> allowedContentTypes) {
}
