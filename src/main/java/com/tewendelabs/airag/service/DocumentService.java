package com.tewendelabs.airag.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.tewendelabs.airag.exceptions.ForbiddenOperationException;
import com.tewendelabs.airag.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tewendelabs.airag.entity.Department;
import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.entity.DocumentStatus;
import com.tewendelabs.airag.entity.Role;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.exceptions.FileValidationException;
import com.tewendelabs.airag.ingestion.FileValidationService;
import com.tewendelabs.airag.ingestion.IngestionPipeline;
import com.tewendelabs.airag.repository.DepartmentRepository;
import com.tewendelabs.airag.repository.DocumentRepository;
import com.tewendelabs.airag.repository.UserRepository;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AccessScopeResolver accessScopeResolver;
    private final FileValidationService fileValidationService;
    private final IngestionPipeline ingestionPipeline;

    public DocumentService(DocumentRepository documentRepository, DepartmentRepository departmentRepository,
            UserRepository userRepository, AccessScopeResolver accessScopeResolver,
            FileValidationService fileValidationService, IngestionPipeline ingestionPipeline) {
        this.documentRepository = documentRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.accessScopeResolver = accessScopeResolver;
        this.fileValidationService = fileValidationService;
        this.ingestionPipeline = ingestionPipeline;
    }

    /**
     * Volontairement PAS {@code @Transactional} : le document doit etre commite en base avant
     * que le pipeline d'ingestion asynchrone (autre thread) ne le relise - sous une transaction
     * englobante, {@code save()} ne serait visible qu'apres le retour de cette methode, creant
     * une course avec le thread async demarre a l'interieur.
     */
    public Document upload(UUID userId, Integer departmentId, MultipartFile file) {
        User user = requireUser(userId);
        requireUploadRole(user);

        List<Integer> authorized = accessScopeResolver.resolveAuthorizedDepartmentIds(user);
        if (!authorized.contains(departmentId)) {
            throw new ForbiddenOperationException("Departement hors du perimetre autorise");
        }
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Departement introuvable"));

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new FileValidationException("Impossible de lire le fichier envoye");
        }
        String contentType = fileValidationService.validate(file, content);

        Document document = Document.builder()
                .department(department)
                .filename(file.getOriginalFilename())
                .contentType(contentType)
                .sizeBytes((long) content.length)
                .status(DocumentStatus.UPLOADED)
                .build();
        Document saved = documentRepository.save(document);
        ingestionPipeline.process(saved.getId(), content);
        return saved;
    }

    public List<Document> listAuthorized(UUID userId, Integer departmentIdFilter) {
        User user = requireUser(userId);
        List<Integer> authorized = accessScopeResolver.resolveAuthorizedDepartmentIds(user);

        if (departmentIdFilter != null) {
            if (!authorized.contains(departmentIdFilter)) {
                throw new ForbiddenOperationException("Departement hors du perimetre autorise");
            }
            return documentRepository.findByDepartmentIdIn(List.of(departmentIdFilter));
        }
        return documentRepository.findByDepartmentIdIn(authorized);
    }

    public Document getAuthorized(UUID userId, UUID documentId) {
        User user = requireUser(userId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));
        requireDepartmentAccess(user, document);
        return document;
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        User user = requireUser(userId);
        requireUploadRole(user);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));
        requireDepartmentAccess(user, document);
        // Les chunks sont supprimes automatiquement (chunks.document_id ... ON DELETE CASCADE, V1).
        documentRepository.delete(document);
    }

    private void requireDepartmentAccess(User user, Document document) {
        List<Integer> authorized = accessScopeResolver.resolveAuthorizedDepartmentIds(user);
        Integer departmentId = document.getDepartment() != null ? document.getDepartment().getId() : null;
        if (departmentId == null || !authorized.contains(departmentId)) {
            throw new ForbiddenOperationException("Document hors du perimetre autorise");
        }
    }

    private void requireUploadRole(User user) {
        String roleCode = user.getRole().getCode();
        if (!Role.MANAGER.equals(roleCode) && !Role.ADMIN.equals(roleCode)) {
            throw new ForbiddenOperationException("Role insuffisant pour cette operation");
        }
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
