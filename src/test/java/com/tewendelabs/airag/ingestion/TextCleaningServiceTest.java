package com.tewendelabs.airag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextCleaningServiceTest {

    private final TextCleaningService service = new TextCleaningService();

    @Test
    void collapsesExcessiveBlankLines() {
        String result = service.clean("Ligne 1\n\n\n\n\nLigne 2");

        assertThat(result).isEqualTo("Ligne 1\n\nLigne 2");
    }

    @Test
    void normalizesWindowsLineEndingsAndCollapsesSpaces() {
        String result = service.clean("Ligne 1\r\n\r\nLigne   2   avec    espaces");

        assertThat(result).isEqualTo("Ligne 1\n\nLigne 2 avec espaces");
    }

    @Test
    void stripsControlCharacters() {
        char controlChar = 7; // BEL, dans la plage de caracteres de controle supprimee
        String withControlChar = "Texte avec" + controlChar + " un caractere de controle";

        String result = service.clean(withControlChar);

        assertThat(result).isEqualTo("Texte avec un caractere de controle");
    }

    @Test
    void collapsesTabsToSingleSpaceButKeepsNewlines() {
        // Les tabulations sont un espacement horizontal comme les espaces : elles sont
        // uniformisees en un espace simple avant chunking, seuls les retours a la ligne
        // structurent le texte.
        String result = service.clean("Colonne1\tColonne2\nLigne suivante");

        assertThat(result).isEqualTo("Colonne1 Colonne2\nLigne suivante");
    }

    @Test
    void returnsEmptyStringForNull() {
        assertThat(service.clean(null)).isEmpty();
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(service.clean("   contenu utile   ")).isEqualTo("contenu utile");
    }
}
