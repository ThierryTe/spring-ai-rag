package com.tewendelabs.airag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "rag.cost")
public record RagCostProperties(double promptPer1kTokensUsd, double completionPer1kTokensUsd) {
}
