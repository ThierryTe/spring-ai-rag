package com.tewendelabs.airag.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.entity.DocumentStatus;
import com.tewendelabs.airag.exceptions.FileValidationException;
import com.tewendelabs.airag.ingestion.FileValidationService;
import com.tewendelabs.airag.ingestion.IngestionPipeline;
import com.tewendelabs.airag.repository.DocumentRepository;


@Service
public class DemoDocumentService {

    private final DocumentRepository documentRepository;
    private final DemoSessionService demoSessionService;
    private final FileValidationService fileValidationService;
    private final IngestionPipeline ingestionPipeline;

    public DemoDocumentService(DocumentRepository documentRepository, DemoSessionService demoSessionService,
            FileValidationService fileValidationService, IngestionPipeline ingestionPipeline) {
        this.documentRepository = documentRepository;
        this.demoSessionService = demoSessionService;
        this.fileValidationService = fileValidationService;
        this.ingestionPipeline = ingestionPipeline;
    }

    /**
     * Volontairement PAS {@code @Transactional}, pour la meme raison que
     * {@code DocumentService.upload} : le document doit etre commite avant que le pipeline
     * d'ingestion asynchrone (autre thread) ne le relise.
     */
    public Document upload(UUID demoSessionId, MultipartFile file) {
        DemoSession session = demoSessionService.requireValid(demoSessionId);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new FileValidationException("Impossible de lire le fichier envoye");
        }
        String contentType = fileValidationService.validate(file, content);
        // Consomme le quota seulement une fois le fichier valide : un upload rejete pour un motif
        // client (vide, trop volumineux, type non autorise) ne doit pas bruler un des 2 slots de
        // la session demo pour rien.
        demoSessionService.consumeDocumentSlot(session.getId());

        Document document = Document.builder()
                .demoSessionId(session.getId())
                .filename(file.getOriginalFilename())
                .contentType(contentType)
                .sizeBytes((long) content.length)
                .status(DocumentStatus.UPLOADED)
                .build();
        Document saved = documentRepository.save(document);
        ingestionPipeline.process(saved.getId(), content);
        return saved;
    }

    public List<Document> listForSession(UUID demoSessionId) {
        demoSessionService.requireValid(demoSessionId);
        return documentRepository.findByDemoSessionId(demoSessionId);
    }
}
