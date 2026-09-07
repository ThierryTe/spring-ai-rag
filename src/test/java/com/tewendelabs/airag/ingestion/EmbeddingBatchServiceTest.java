package com.tewendelabs.airag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

class EmbeddingBatchServiceTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final EmbeddingBatchService service = new EmbeddingBatchService(embeddingModel);

    @Test
    void returnsEmptyListWithoutCallingModelWhenNoChunks() {
        List<EmbeddedChunk> result = service.embed(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void pairsEachChunkWithItsEmbeddingPreservingOrder() {
        List<TextChunk> chunks = List.of(
                new TextChunk(0, "premier extrait"),
                new TextChunk(1, "deuxieme extrait"),
                new TextChunk(2, "troisieme extrait"));
        float[] vector0 = {1f, 0f};
        float[] vector1 = {0f, 1f};
        float[] vector2 = {1f, 1f};
        when(embeddingModel.embed(anyList())).thenReturn(List.of(vector0, vector1, vector2));

        List<EmbeddedChunk> result = service.embed(chunks);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).chunk()).isEqualTo(chunks.get(0));
        assertThat(result.get(0).embedding()).isEqualTo(vector0);
        assertThat(result.get(1).chunk()).isEqualTo(chunks.get(1));
        assertThat(result.get(1).embedding()).isEqualTo(vector1);
        assertThat(result.get(2).chunk()).isEqualTo(chunks.get(2));
        assertThat(result.get(2).embedding()).isEqualTo(vector2);
    }

    @Test
    void sendsAllChunkContentsInASingleBatchCall() {
        List<TextChunk> chunks = List.of(new TextChunk(0, "a"), new TextChunk(1, "b"));
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {1f}, new float[] {2f}));

        service.embed(chunks);

        verify(embeddingModel).embed(List.of("a", "b"));
    }
}
