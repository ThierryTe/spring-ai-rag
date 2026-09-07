package com.tewendelabs.airag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tewendelabs.airag.config.ChunkingProperties;

class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService(new ChunkingProperties(100, 20));

    @Test
    void returnsEmptyListForBlankText() {
        assertThat(service.chunk("")).isEmpty();
        assertThat(service.chunk("   ")).isEmpty();
        assertThat(service.chunk(null)).isEmpty();
    }

    @Test
    void returnsSingleChunkWhenTextFitsInOneWindow() {
        String text = "Une courte politique de conges.";

        List<TextChunk> chunks = service.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).content()).isEqualTo(text);
    }

    @Test
    void splitsLongTextIntoMultipleChunksRespectingSizeLimit() {
        String sentence = "Ceci est une phrase de taille raisonnable pour le test. ";
        String longText = sentence.repeat(10); // ~580 caracteres, chunkSize=100

        List<TextChunk> chunks = service.chunk(longText);

        assertThat(chunks.size()).isGreaterThan(1);
        for (TextChunk chunk : chunks) {
            assertThat(chunk.content()).isNotBlank();
        }
    }

    @Test
    void chunkIndexesAreSequentialStartingAtZero() {
        String sentence = "Regle de conformite numero un applicable a tout le monde. ";
        String longText = sentence.repeat(10);

        List<TextChunk> chunks = service.chunk(longText);

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
        }
    }

    @Test
    void consecutiveChunksOverlap() {
        String sentence = "Phrase numero %d courte. ";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            text.append(String.format(sentence, i));
        }

        List<TextChunk> chunks = service.chunk(text.toString());

        assertThat(chunks.size()).isGreaterThan(1);
        // Le chevauchement garantit que la fin du premier chunk reapparait au debut du second.
        String firstChunkContent = chunks.get(0).content();
        String overlapFragment = firstChunkContent.substring(Math.max(0, firstChunkContent.length() - 10));
        assertThat(chunks.get(1).content()).contains(overlapFragment);
    }

    @Test
    void hardSplitsASingleSentenceLongerThanChunkSize() {
        String noPunctuation = "mot".repeat(100); // 300 caracteres sans ponctuation, chunkSize=100

        List<TextChunk> chunks = service.chunk(noPunctuation);

        assertThat(chunks.size()).isGreaterThan(1);
        for (TextChunk chunk : chunks) {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(100);
        }
    }
}
