package com.tewendelabs.airag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.session")
public record DemoSessionProperties(int maxQuestions, int maxDocuments, int ttlHours) {
}
