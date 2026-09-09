package com.tewendelabs.airag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.entity.QueryLog;
import com.tewendelabs.airag.repository.DemoSessionRepository;
import com.tewendelabs.airag.repository.DocumentRepository;
import com.tewendelabs.airag.repository.QueryLogRepository;

/**
 * Couvre le mode demo (Phase 8) de bout en bout avec un vrai Postgres (Testcontainers) : les
 * contraintes CHECK {@code chk_document_scope}/{@code chk_query_owner} et la cascade de la
 * migration V5 sont exercees pour de vrai (pas mockees), pas seulement les codes HTTP.
 */
@AutoConfigureMockMvc
class DemoSessionControllerIntegrationTest extends com.tewendelabs.airag.AbstractIntegrationTest {

    private static final String SESSION_HEADER = "X-Demo-Session-Id";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DemoSessionRepository demoSessionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private QueryLogRepository queryLogRepository;

    @Test
    void createSession_returnsQuotasFromConfiguration() throws Exception {
        mvc.perform(post("/api/demo/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").exists())
                // expiresAt doit porter un fuseau explicite (UTC) : sans "Z", le frontend
                // interprete la date comme une heure locale du navigateur (voir toResponse()).
                .andExpect(jsonPath("$.expiresAt", endsWith("Z")))
                .andExpect(jsonPath("$.maxQuestions").value(5))
                .andExpect(jsonPath("$.maxDocuments").value(2))
                .andExpect(jsonPath("$.questionsUsed").value(0))
                .andExpect(jsonPath("$.documentsUsed").value(0));
    }

    @Test
    void sessionEndpoints_withoutHeader_return401() throws Exception {
        mvc.perform(get("/api/demo/sessions/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/demo/documents")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/demo/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Une question\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionEndpoints_withMalformedHeader_return401() throws Exception {
        mvc.perform(get("/api/demo/sessions/me").header(SESSION_HEADER, "not-a-uuid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionEndpoints_withUnknownSessionId_return401() throws Exception {
        mvc.perform(get("/api/demo/sessions/me").header(SESSION_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadDocument_persistsWithDemoSessionScopeAndRespectsXorConstraint() throws Exception {
        DemoSession session = createSession();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "Contenu demo".getBytes(StandardCharsets.UTF_8));

        String body = mvc.perform(multipart("/api/demo/documents")
                        .file(file)
                        .header(SESSION_HEADER, session.getId().toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.documentId").toString());

        // Verifie reellement en base (pas un mock) que chk_document_scope est respectee :
        // demo_session_id renseigne, department_id null.
        Document persisted = documentRepository.findById(documentId).orElseThrow();
        assertThat(persisted.getDemoSessionId()).isEqualTo(session.getId());
        assertThat(persisted.getDepartment()).isNull();

        mvc.perform(get("/api/demo/documents").header(SESSION_HEADER, session.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + documentId + "')]").exists());
    }

    @Test
    void uploadDocument_quotaExceeded_returns429() throws Exception {
        DemoSession session = createSession();
        for (int i = 0; i < 2; i++) {
            MockMultipartFile file = new MockMultipartFile("file", "doc" + i + ".txt", "text/plain",
                    ("Contenu " + i).getBytes(StandardCharsets.UTF_8));
            mvc.perform(multipart("/api/demo/documents")
                            .file(file)
                            .header(SESSION_HEADER, session.getId().toString()))
                    .andExpect(status().isAccepted());
        }

        MockMultipartFile thirdFile = new MockMultipartFile("file", "trop.txt", "text/plain",
                "Contenu en trop".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/demo/documents")
                        .file(thirdFile)
                        .header(SESSION_HEADER, session.getId().toString()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void uploadDocument_rejectedByValidation_doesNotConsumeQuotaSlot() throws Exception {
        // Epingle l'ordre validation-puis-consommation dans DemoDocumentService.upload : un
        // fichier vide est rejete par FileValidationService (400) et ne doit PAS couter un des 2
        // slots de la session - sinon ce test resterait vert meme si l'ordre etait a nouveau
        // inverse (le seul autre test de quota n'utilise que des fichiers valides).
        DemoSession session = createSession();
        MockMultipartFile emptyFile = new MockMultipartFile("file", "vide.txt", "text/plain", new byte[0]);

        mvc.perform(multipart("/api/demo/documents")
                        .file(emptyFile)
                        .header(SESSION_HEADER, session.getId().toString()))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/demo/sessions/me").header(SESSION_HEADER, session.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsUsed").value(0));
    }

    @Test
    void chat_persistsQueryLogWithDemoSessionScopeAndRespectsXorConstraint() throws Exception {
        DemoSession session = createSession();
        long before = queryLogRepository.count();

        mvc.perform(post("/api/demo/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SESSION_HEADER, session.getId().toString())
                        .content("{\"question\":\"Une question sans contexte\"}"))
                .andExpect(status().isOk());

        assertThat(queryLogRepository.count()).isEqualTo(before + 1);
        // Verifie reellement en base que chk_query_owner est respectee : demo_session_id
        // renseigne, user_id null.
        QueryLog log = queryLogRepository.findAll().stream()
                .filter(l -> session.getId().equals(l.getDemoSessionId()))
                .findFirst().orElseThrow();
        assertThat(log.getUserId()).isNull();
    }

    @Test
    void chat_questionQuotaExceeded_persistsLogThenReturns429() throws Exception {
        DemoSession session = createSession();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/demo/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(SESSION_HEADER, session.getId().toString())
                            .content("{\"question\":\"Question numero " + i + "\"}"))
                    .andExpect(status().isOk());
        }
        long before = queryLogRepository.count();

        mvc.perform(post("/api/demo/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SESSION_HEADER, session.getId().toString())
                        .content("{\"question\":\"Question de trop\"}"))
                .andExpect(status().isTooManyRequests());

        assertThat(queryLogRepository.count()).isEqualTo(before + 1);
        QueryLog log = queryLogRepository.findAll().stream()
                .filter(l -> session.getId().equals(l.getDemoSessionId()) && !l.isAccessAllowed())
                .filter(l -> "QUOTA_EXCEEDED".equals(l.getRefusalReason()))
                .findFirst().orElseThrow();
        assertThat(log.getRefusalReason()).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    void chat_expiredSession_refusesGracefullyWith200() throws Exception {
        DemoSession session = demoSessionRepository.save(DemoSession.builder()
                .ipAddress("127.0.0.1")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build());

        mvc.perform(post("/api/demo/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SESSION_HEADER, session.getId().toString())
                        .content("{\"question\":\"Une question\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.refusalReason").value("SESSION_EXPIRED"));
    }

    @Test
    void nonChatEndpoints_withExpiredSession_return401() throws Exception {
        DemoSession session = demoSessionRepository.save(DemoSession.builder()
                .ipAddress("127.0.0.1")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build());

        mvc.perform(get("/api/demo/sessions/me").header(SESSION_HEADER, session.getId().toString()))
                .andExpect(status().isUnauthorized());
    }

    private DemoSession createSession() {
        return demoSessionRepository.save(DemoSession.builder()
                .ipAddress("127.0.0.1")
                .expiresAt(LocalDateTime.now().plusHours(2))
                .build());
    }
}
