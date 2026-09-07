package com.tewendelabs.airag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.tewendelabs.airag.exceptions.FileValidationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.tewendelabs.airag.config.IngestionProperties;

class FileValidationServiceTest {

    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain");

    private final FileValidationService service = new FileValidationService(
            new IngestionProperties(1, 200, ALLOWED_TYPES));

    @Test
    void acceptsRealPdf() throws Exception {
        byte[] pdfBytes = generatePdf(1);
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", pdfBytes);

        String detectedType = service.validate(file, pdfBytes);

        assertThat(detectedType).isEqualTo("application/pdf");
    }

    @Test
    void acceptsPlainText() {
        byte[] content = "Politique de conges - v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", content);

        String detectedType = service.validate(file, content);

        assertThat(detectedType).isEqualTo("text/plain");
    }

    @Test
    void rejectsFileAboveMaxSize() {
        byte[] content = new byte[2 * 1024 * 1024]; // 2 Mo > limite de test (1 Mo)
        MockMultipartFile file = new MockMultipartFile("file", "gros.txt", "text/plain", content);

        assertThatThrownBy(() -> service.validate(file, content))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("taille maximale");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "vide.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.validate(file, new byte[0]))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("vide");
    }

    @Test
    void rejectsRealContentTypeNotInAllowlistRegardlessOfDeclaredContentType() {
        // Signature PNG reelle (89 50 4E 47 0D 0A 1A 0A) : Tika doit detecter image/png a partir
        // du contenu, meme si le client pretend qu'il s'agit d'un PDF.
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "image-deguisee.pdf", "application/pdf", pngBytes);

        assertThatThrownBy(() -> service.validate(file, pngBytes))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("non autorise");
    }

    @Test
    void rejectsDangerousFilenameWithPathTraversal() {
        byte[] content = "contenu".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd", "text/plain", content);

        assertThatThrownBy(() -> service.validate(file, content))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("invalide");
    }

    private static byte[] generatePdf(int pageCount) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
