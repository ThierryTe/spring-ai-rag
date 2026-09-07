package com.tewendelabs.airag.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void includesStaticRulesAndNumberedContext() {
        CitedContext first = citation(1, "Contenu du premier extrait");
        CitedContext second = citation(2, "Contenu du second extrait");

        String systemPrompt = promptBuilder.buildSystemPrompt(List.of(first, second));

        assertThat(systemPrompt).contains("CONTEXTE");
        assertThat(systemPrompt).contains("[1] Contenu du premier extrait");
        assertThat(systemPrompt).contains("[2] Contenu du second extrait");
        assertThat(systemPrompt).contains("Tu es l'assistant de conformite interne");
    }

    @Test
    void usesTheCitedContextReferenceNumberNotListPosition() {
        // Le numero affiche doit venir de referenceNumber(), pas de la position dans la liste :
        // c'est ce qui garantit que le numero vu par le modele reste identique a celui renvoye au
        // client, meme si CitationBuilder produit un jour un ordre different de l'index brut.
        CitedContext citation = citation(7, "Contenu quelconque");

        String systemPrompt = promptBuilder.buildSystemPrompt(List.of(citation));

        assertThat(systemPrompt).contains("[7] Contenu quelconque");
    }

    @Test
    void survivesChunkContentContainingLiteralBraces() {
        CitedContext withBraces = citation(1, "Exemple de configuration : { \"cle\": \"valeur\" }");

        String systemPrompt = promptBuilder.buildSystemPrompt(List.of(withBraces));

        assertThat(systemPrompt).contains("[1] Exemple de configuration : { \"cle\": \"valeur\" }");
    }

    @Test
    void emptyContextStillProducesSystemPrompt() {
        String systemPrompt = promptBuilder.buildSystemPrompt(List.of());

        assertThat(systemPrompt).contains("CONTEXTE");
    }

    private static CitedContext citation(int referenceNumber, String content) {
        return new CitedContext(referenceNumber, "titre", "fichier.txt", null, null, 0.9, content);
    }
}
