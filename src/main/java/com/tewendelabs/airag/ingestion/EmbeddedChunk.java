package com.tewendelabs.airag.ingestion;

public record EmbeddedChunk(TextChunk chunk, float[] embedding) {
}
