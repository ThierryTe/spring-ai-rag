package com.tewendelabs.airag.ingestion;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingBatchService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingBatchService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<EmbeddedChunk> embed(List<TextChunk> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<String> texts = chunks.stream().map(TextChunk::content).toList();
        List<float[]> embeddings = embeddingModel.embed(texts);

        List<EmbeddedChunk> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            result.add(new EmbeddedChunk(chunks.get(i), embeddings.get(i)));
        }
        return result;
    }
}
