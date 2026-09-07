package com.tewendelabs.airag.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tewendelabs.airag.dto.DocumentSummaryResponse;
import com.tewendelabs.airag.dto.DocumentUploadResponse;
import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.service.DocumentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Upload et gestion des documents ingeres dans la base de connaissances")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Uploader un document", description = "Reserve aux roles MANAGER/ADMIN. Declenche "
            + "l'ingestion asynchrone (extraction, chunking, embeddings) ; le statut passe par "
            + "UPLOADED -> PROCESSING -> INDEXED/FAILED.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("departmentId") Integer departmentId) {
        Document document = documentService.upload(userId, departmentId, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toUploadResponse(document));
    }

    @Operation(summary = "Lister les documents accessibles", description = "Filtre par le perimetre "
            + "departements autorise pour l'utilisateur courant.")
    @GetMapping
    public List<DocumentSummaryResponse> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(value = "departmentId", required = false) Integer departmentId) {
        return documentService.listAuthorized(userId, departmentId).stream()
                .map(DocumentController::toSummary)
                .toList();
    }

    @Operation(summary = "Consulter un document")
    @GetMapping("/{id}")
    public DocumentSummaryResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return toSummary(documentService.getAuthorized(userId, id));
    }

    @Operation(summary = "Suivre le statut d'ingestion d'un document", description = "A interroger en polling "
            + "apres l'upload jusqu'a INDEXED ou FAILED.")
    @GetMapping("/{id}/status")
    public DocumentUploadResponse status(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return toUploadResponse(documentService.getAuthorized(userId, id));
    }

    @Operation(summary = "Supprimer un document", description = "Reserve aux roles MANAGER/ADMIN. "
            + "Les chunks associes sont supprimes en cascade.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        documentService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private static DocumentUploadResponse toUploadResponse(Document document) {
        return new DocumentUploadResponse(document.getId(), document.getStatus());
    }

    private static DocumentSummaryResponse toSummary(Document document) {
        return new DocumentSummaryResponse(document.getId(), document.getFilename(), document.getTitle(),
                document.getStatus(), document.getPageCount(), document.getIngestedAt());
    }
}
