package com.tewendelabs.airag.ingestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tewendelabs.airag.config.ChunkingProperties;

@Service
public class ChunkingService {

    private final ChunkingProperties properties;

    public ChunkingService(ChunkingProperties properties) {
        this.properties = properties;
    }

    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Les unites brutes sont taillees a chunkSize - chunkOverlap, pas chunkSize : une unite
        // deja pleine plus le chevauchement reporte du chunk precedent depasserait sinon
        // systematiquement la limite (chevauchement + unite a taille max > chunkSize).
        int hardSplitSize = Math.max(1, properties.chunkSize() - properties.chunkOverlap());

        List<String> units = new ArrayList<>();
        for (String sentence : splitIntoSentences(text)) {
            if (sentence.length() > properties.chunkSize()) {
                units.addAll(hardSplit(sentence, hardSplitSize));
            } else {
                units.add(sentence);
            }
        }

        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (!current.isEmpty() && current.length() + unit.length() + 1 > properties.chunkSize()) {
                chunks.add(new TextChunk(chunks.size(), current.toString().trim()));
                current = new StringBuilder(tailForOverlap(current.toString(), properties.chunkOverlap()));
            }
            current.append(unit).append(' ');
        }
        if (!current.toString().isBlank()) {
            chunks.add(new TextChunk(chunks.size(), current.toString().trim()));
        }
        return chunks;
    }

    private List<String> splitIntoSentences(String text) {
        return Arrays.stream(text.split("(?<=[.!?])\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> hardSplit(String text, int size) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            parts.add(text.substring(i, Math.min(i + size, text.length())));
        }
        return parts;
    }

    private String tailForOverlap(String text, int overlapChars) {
        if (overlapChars <= 0 || text.length() <= overlapChars) {
            return text.isBlank() ? "" : text;
        }
        return text.substring(text.length() - overlapChars);
    }
}
