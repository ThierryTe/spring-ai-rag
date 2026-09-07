package com.tewendelabs.airag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tewendelabs.airag.AbstractIntegrationTest;
import com.tewendelabs.airag.entity.Department;
import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.entity.DocumentChunk;
import com.tewendelabs.airag.entity.DocumentStatus;
import com.tewendelabs.airag.repository.ChunkRepository;
import com.tewendelabs.airag.repository.DepartmentRepository;
import com.tewendelabs.airag.repository.DocumentRepository;

/**
 * Le pipeline etant @Async, ces tests attendent la transition de statut finale via Awaitility
 * plutot que de bloquer sur un futur - c'est le meme modele que la production (fire-and-forget
 * depuis DocumentService.upload).
 */
class IngestionPipelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IngestionPipeline ingestionPipeline;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @Test
    void indexesDocumentEndToEndWithMockedEmbeddings() {
        Department general = departmentRepository.findByCode("GENERAL").orElseThrow();
        Document document = documentRepository.save(Document.builder()
                .department(general)
                .filename("politique.txt")
                .status(DocumentStatus.UPLOADED)
                .build());
        byte[] content = "Politique de conges de l'entreprise, applicable a tout le personnel. "
                .repeat(50)
                .getBytes(StandardCharsets.UTF_8);

        ingestionPipeline.process(document.getId(), content);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Document reloaded = documentRepository.findById(document.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        });

        List<DocumentChunk> chunks = chunkRepository.findByDocumentId(document.getId());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getContent()).isNotBlank());
    }

    @Test
    void marksDocumentFailedWhenNoExtractableContent() {
        Department general = departmentRepository.findByCode("GENERAL").orElseThrow();
        Document document = documentRepository.save(Document.builder()
                .department(general)
                .filename("vide.txt")
                .status(DocumentStatus.UPLOADED)
                .build());

        ingestionPipeline.process(document.getId(), new byte[0]);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Document reloaded = documentRepository.findById(document.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.FAILED);
            assertThat(reloaded.getFailureReason()).isNotBlank();
        });

        assertThat(chunkRepository.findByDocumentId(document.getId())).isEmpty();
    }
}
