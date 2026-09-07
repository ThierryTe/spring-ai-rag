package com.tewendelabs.airag.ingestion;

import java.util.List;
import java.util.UUID;

import com.tewendelabs.airag.exceptions.IngestionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.tewendelabs.airag.config.AsyncConfig;
import com.tewendelabs.airag.config.IngestionProperties;
import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.entity.DocumentStatus;
import com.tewendelabs.airag.repository.DocumentRepository;


@Component
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final DocumentRepository documentRepository;
    private final TextExtractionService textExtractionService;
    private final TextCleaningService textCleaningService;
    private final ChunkingService chunkingService;
    private final EmbeddingBatchService embeddingBatchService;
    private final ChunkPersistenceWriter chunkPersistenceWriter;
    private final IngestionProperties ingestionProperties;

    public IngestionPipeline(DocumentRepository documentRepository, TextExtractionService textExtractionService,
            TextCleaningService textCleaningService, ChunkingService chunkingService,
            EmbeddingBatchService embeddingBatchService, ChunkPersistenceWriter chunkPersistenceWriter,
            IngestionProperties ingestionProperties) {
        this.documentRepository = documentRepository;
        this.textExtractionService = textExtractionService;
        this.textCleaningService = textCleaningService;
        this.chunkingService = chunkingService;
        this.embeddingBatchService = embeddingBatchService;
        this.chunkPersistenceWriter = chunkPersistenceWriter;
        this.ingestionProperties = ingestionProperties;
    }

    @Async(AsyncConfig.INGESTION_EXECUTOR)
    public void process(UUID documentId, byte[] content) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("Document {} introuvable au demarrage de l'ingestion (supprime entre temps ?)", documentId);
            return;
        }

        try {
            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);

            ExtractedDocument extracted = textExtractionService.extract(content);
            if (extracted.pageCount() != null && extracted.pageCount() > ingestionProperties.maxPages()) {
                throw new IngestionException("Nombre de pages (" + extracted.pageCount()
                        + ") superieur a la limite autorisee (" + ingestionProperties.maxPages() + ")");
            }

            String cleanedText = textCleaningService.clean(extracted.text());
            List<TextChunk> chunks = chunkingService.chunk(cleanedText);
            if (chunks.isEmpty()) {
                throw new IngestionException("Aucun contenu exploitable extrait du document");
            }

            List<EmbeddedChunk> embeddedChunks = embeddingBatchService.embed(chunks);
            chunkPersistenceWriter.persist(document, embeddedChunks);

            document.setPageCount(extracted.pageCount());
            document.setStatus(DocumentStatus.INDEXED);
            documentRepository.save(document);
        } catch (Exception e) {
            log.warn("Echec de l'ingestion du document {} : {}", documentId, e.getMessage());
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason(truncate(e.getMessage(), MAX_FAILURE_REASON_LENGTH));
            documentRepository.save(document);
        }
    }

    private static String truncate(String message, int maxLength) {
        if (message == null) {
            return "Erreur inconnue lors de l'ingestion";
        }
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}
