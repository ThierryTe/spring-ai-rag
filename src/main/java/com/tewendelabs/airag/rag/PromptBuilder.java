package com.tewendelabs.airag.rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;


@Component
public class PromptBuilder {

    private final String staticSystemRules;

    public PromptBuilder() {
        try {
            this.staticSystemRules = StreamUtils.copyToString(
                    new ClassPathResource("prompts/rag-system-prompt.st").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de charger prompts/rag-system-prompt.st", e);
        }
    }

    public String buildSystemPrompt(List<CitedContext> context) {
        StringBuilder sb = new StringBuilder(staticSystemRules);
        sb.append("\n\nCONTEXTE :\n");
        for (CitedContext citedContext : context) {
            sb.append('[').append(citedContext.referenceNumber()).append("] ")
                    .append(citedContext.content()).append('\n');
        }
        return sb.toString();
    }
}
