package com.tewendelabs.airag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieval")
public record RagProperties(int topK, double minSimilarity, int dailyQuestionQuotaPerUser) {
}
