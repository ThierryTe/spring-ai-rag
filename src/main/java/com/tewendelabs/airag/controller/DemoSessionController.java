package com.tewendelabs.airag.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tewendelabs.airag.config.DemoSessionProperties;
import com.tewendelabs.airag.dto.ChatAnswerResponse;
import com.tewendelabs.airag.dto.ChatRequest;
import com.tewendelabs.airag.dto.DemoSessionResponse;
import com.tewendelabs.airag.dto.DocumentSummaryResponse;
import com.tewendelabs.airag.dto.DocumentUploadResponse;
import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.exceptions.InvalidDemoSessionException;
import com.tewendelabs.airag.rag.RagQueryService;
import com.tewendelabs.airag.service.DemoDocumentService;
import com.tewendelabs.airag.service.DemoSessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/demo")
@Tag(name = "Mode demo", description = "Session anonyme a quotas (sans JWT) : identifiee par l'en-tete "
        + "X-Demo-Session-Id renvoye a la creation")
public class DemoSessionController {

    private static final String SESSION_HEADER = "X-Demo-Session-Id";

    private final DemoSessionService demoSessionService;
    private final DemoDocumentService demoDocumentService;
    private final RagQueryService ragQueryService;
    private final DemoSessionProperties demoSessionProperties;

    public DemoSessionController(DemoSessionService demoSessionService, DemoDocumentService demoDocumentService,
            RagQueryService ragQueryService, DemoSessionProperties demoSessionProperties) {
        this.demoSessionService = demoSessionService;
        this.demoDocumentService = demoDocumentService;
        this.ragQueryService = ragQueryService;
        this.demoSessionProperties = demoSessionProperties;
    }

    @Operation(summary = "Creer une session demo", description = "Accessible sans authentification. Retourne "
            + "l'identifiant de session a transmettre dans l'en-tete " + SESSION_HEADER + " sur les appels "
            + "suivants.")
    @PostMapping("/sessions")
    public ResponseEntity<DemoSessionResponse> createSession(HttpServletRequest httpRequest) {
        DemoSession session = demoSessionService.create(httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session));
    }

    private DemoSessionResponse toResponse(DemoSession session) {
        return new DemoSessionResponse(session.getId(), session.getExpiresAt(),
                demoSessionProperties.maxQuestions(), demoSessionProperties.maxDocuments(),
                session.getQuestionsUsed(), session.getDocumentsUsed());
    }

    @Operation(summary = "Consulter les quotas de la session", description = "401 si l'en-tete "
            + SESSION_HEADER + " est absent, invalide, inconnu ou correspond a une session expiree.")
    @GetMapping("/sessions/me")
    public DemoSessionResponse getSession(@RequestHeader(value = SESSION_HEADER, required = false) String header) {
        return toResponse(demoSessionService.requireValid(parseSessionId(header)));
    }

    @Operation(summary = "Uploader un document dans la session demo", description = "429 si le quota de "
            + "documents de la session est atteint.")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestHeader(value = SESSION_HEADER, required = false) String header,
            @RequestParam("file") MultipartFile file) {
        Document document = demoDocumentService.upload(parseSessionId(header), file);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new DocumentUploadResponse(document.getId(), document.getStatus()));
    }

    @Operation(summary = "Lister les documents de la session demo")
    @GetMapping("/documents")
    public List<DocumentSummaryResponse> listDocuments(
            @RequestHeader(value = SESSION_HEADER, required = false) String header) {
        return demoDocumentService.listForSession(parseSessionId(header)).stream()
                .map(d -> new DocumentSummaryResponse(d.getId(), d.getFilename(), d.getTitle(), d.getStatus(),
                        d.getPageCount(), d.getIngestedAt()))
                .toList();
    }

    @Operation(summary = "Poser une question en mode demo", description = "429 si le quota de questions est "
            + "atteint. Peut renvoyer refused=true (200) si la session vient d'expirer, la question porte sur "
            + "un sujet sensible, ou aucun contexte pertinent n'est trouve.")
    @PostMapping("/chat")
    public ChatAnswerResponse chat(@RequestHeader(value = SESSION_HEADER, required = false) String header,
            @RequestBody ChatRequest request) {
        return ragQueryService.answerAsDemo(parseSessionId(header), request);
    }

    /**
     * L'en-tete est lu en {@code String} (pas directement en {@code UUID}) : une liaison Spring
     * directe sur un type {@code UUID} ferait passer un en-tete absent ou malforme par les
     * exceptions MVC standard de {@code ResponseEntityExceptionHandler} (400), alors que le
     * contrat de cette API veut un 401 uniforme pour toute session non identifiable.
     */
    private static UUID parseSessionId(String header) {
        if (header == null || header.isBlank()) {
            throw new InvalidDemoSessionException("En-tete " + SESSION_HEADER + " manquant");
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            throw new InvalidDemoSessionException("En-tete " + SESSION_HEADER + " invalide");
        }
    }
}
