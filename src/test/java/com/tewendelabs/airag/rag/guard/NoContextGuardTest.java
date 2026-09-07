package com.tewendelabs.airag.rag.guard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tewendelabs.airag.repository.ChunkSearchResult;

class NoContextGuardTest {

    private final NoContextGuard guard = new NoContextGuard();

    @Test
    void emptyResults_refuses() {
        assertThatThrownBy(() -> guard.check(List.of(), 0.5))
                .isInstanceOf(RagRefusalException.class)
                .extracting(ex -> ((RagRefusalException) ex).getReason())
                .isEqualTo(RefusalReason.NO_RELEVANT_CONTEXT);
    }

    @Test
    void bestSimilarityBelowThreshold_refuses() {
        List<ChunkSearchResult> results = List.of(chunkWithSimilarity(0.3));

        assertThatThrownBy(() -> guard.check(results, 0.5))
                .isInstanceOf(RagRefusalException.class);
    }

    @Test
    void bestSimilarityAtOrAboveThreshold_passes() {
        List<ChunkSearchResult> results = List.of(chunkWithSimilarity(0.5));

        assertThatCode(() -> guard.check(results, 0.5)).doesNotThrowAnyException();
    }

    private static ChunkSearchResult chunkWithSimilarity(double similarity) {
        return new ChunkSearchResult(UUID.randomUUID(), UUID.randomUUID(), "titre", "fichier.txt", null, 0, null,
                "contenu", similarity);
    }
}
