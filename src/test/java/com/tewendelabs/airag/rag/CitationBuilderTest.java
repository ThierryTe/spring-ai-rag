package com.tewendelabs.airag.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tewendelabs.airag.repository.ChunkSearchResult;

class CitationBuilderTest {

    private final CitationBuilder citationBuilder = new CitationBuilder();

    @Test
    void distinctDocumentsProduceOneCitationEach() {
        ChunkSearchResult docA = chunk(UUID.randomUUID(), "Document A", 0, "Extrait A", 0.9);
        ChunkSearchResult docB = chunk(UUID.randomUUID(), "Document B", 0, "Extrait B", 0.8);

        List<CitedContext> result = citationBuilder.build(List.of(docA, docB));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).referenceNumber()).isEqualTo(1);
        assertThat(result.get(0).documentTitle()).isEqualTo("Document A");
        assertThat(result.get(1).referenceNumber()).isEqualTo(2);
        assertThat(result.get(1).documentTitle()).isEqualTo("Document B");
    }

    @Test
    void multipleChunksFromSameDocumentCollapseIntoOneCitation() {
        UUID documentId = UUID.randomUUID();
        ChunkSearchResult first = chunk(documentId, "Politique RH", 0, "Premiere partie.", 0.7);
        ChunkSearchResult second = chunk(documentId, "Politique RH", 1, "Deuxieme partie.", 0.95);
        ChunkSearchResult third = chunk(documentId, "Politique RH", 2, "Troisieme partie.", 0.6);

        List<CitedContext> result = citationBuilder.build(List.of(first, second, third));

        assertThat(result).hasSize(1);
        CitedContext citation = result.get(0);
        assertThat(citation.referenceNumber()).isEqualTo(1);
        assertThat(citation.documentTitle()).isEqualTo("Politique RH");
        // Contenu concatene dans l'ordre chunkIndex, pas l'ordre d'arrivee dans la liste.
        assertThat(citation.content()).isEqualTo("Premiere partie.\nDeuxieme partie.\nTroisieme partie.");
        // Meilleure preuve de pertinence du groupe = similarite maximale, pas la premiere/derniere.
        assertThat(citation.similarityScore()).isEqualTo(0.95);
    }

    @Test
    void concatenatesInChunkIndexOrderEvenWhenResultsArriveOutOfOrder() {
        UUID documentId = UUID.randomUUID();
        ChunkSearchResult indexTwo = chunk(documentId, "Doc", 2, "C", 0.5);
        ChunkSearchResult indexZero = chunk(documentId, "Doc", 0, "A", 0.9);
        ChunkSearchResult indexOne = chunk(documentId, "Doc", 1, "B", 0.7);

        List<CitedContext> result = citationBuilder.build(List.of(indexTwo, indexZero, indexOne));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("A\nB\nC");
    }

    @Test
    void groupOrderFollowsFirstOccurrenceInTheInputList() {
        // ChunkSearchRepository trie deja par similarite decroissante : preserver l'ordre de
        // premiere apparition revient a garder ce tri au niveau des citations regroupees.
        UUID mostRelevant = UUID.randomUUID();
        UUID lessRelevant = UUID.randomUUID();
        ChunkSearchResult first = chunk(mostRelevant, "Le plus pertinent", 0, "x", 0.95);
        ChunkSearchResult second = chunk(lessRelevant, "Le moins pertinent", 0, "y", 0.6);

        List<CitedContext> result = citationBuilder.build(List.of(first, second));

        assertThat(result.get(0).documentTitle()).isEqualTo("Le plus pertinent");
        assertThat(result.get(1).documentTitle()).isEqualTo("Le moins pertinent");
    }

    @Test
    void emptyResultsProduceNoCitations() {
        assertThat(citationBuilder.build(List.of())).isEmpty();
    }

    private static ChunkSearchResult chunk(UUID documentId, String title, int chunkIndex, String content,
            double similarity) {
        return new ChunkSearchResult(UUID.randomUUID(), documentId, title, "fichier.pdf", null, chunkIndex, null,
                content, similarity);
    }
}
