package com.tewendelabs.airag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

/**
 * Verifie le comportement reel de Tika (aucun mock) : nombre de pages via la metadonnee standard
 * xmpTPg:NPages pour un PDF multi-pages genere par PDFBox, absence de cette metadonnee pour
 * DOCX/TXT (limite documentee dans le plan de conception).
 */
class TextExtractionServiceTest {

    private final TextExtractionService service = new TextExtractionService();

    @Test
    void extractsTextAndPageCountFromMultiPagePdf() throws Exception {
        byte[] pdf = generatePdf("Politique de conges", 3);

        ExtractedDocument result = service.extract(pdf);

        assertThat(result.pageCount()).isEqualTo(3);
        assertThat(result.text()).contains("Politique de conges");
    }

    @Test
    void extractsTextFromDocxWithoutPageCount() throws Exception {
        byte[] docx = generateDocx("Procedure informatique interne");

        ExtractedDocument result = service.extract(docx);

        assertThat(result.text()).contains("Procedure informatique interne");
        assertThat(result.pageCount()).isNull();
    }

    @Test
    void extractsTextFromPlainTextWithoutPageCount() {
        byte[] txt = "Regle RH : delai de preavis de 30 jours".getBytes(StandardCharsets.UTF_8);

        ExtractedDocument result = service.extract(txt);

        assertThat(result.text()).contains("Regle RH");
        assertThat(result.pageCount()).isNull();
    }

    private static byte[] generatePdf(String text, int pageCount) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text + " - page " + (i + 1));
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] generateDocx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            document.write(out);
            return out.toByteArray();
        }
    }
}
