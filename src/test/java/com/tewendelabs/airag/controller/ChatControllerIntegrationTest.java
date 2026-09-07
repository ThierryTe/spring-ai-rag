package com.tewendelabs.airag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.tewendelabs.airag.entity.Department;
import com.tewendelabs.airag.entity.Role;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.repository.ChunkSearchRepository;
import com.tewendelabs.airag.repository.DepartmentRepository;
import com.tewendelabs.airag.repository.QueryLogRepository;
import com.tewendelabs.airag.repository.RoleRepository;
import com.tewendelabs.airag.repository.UserRepository;
import com.tewendelabs.airag.security.JwtService;

@AutoConfigureMockMvc
class ChatControllerIntegrationTest extends com.tewendelabs.airag.AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ChunkSearchRepository chunkSearchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueryLogRepository queryLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private Department rhDepartment;
    private Department financeDepartment;
    private Department itDepartment;
    private Department operationsDepartment;

    @BeforeEach
    void setUpDepartments() {
        // RH est utilise (plutot que GENERAL) pour la recherche par similarite car aucun autre
        // test de la suite n'y insere de chunks : GENERAL accumule des chunks reels via
        // IngestionPipelineIntegrationTest, ce qui rendrait l'ordre topK non deterministe ici.
        rhDepartment = departmentRepository.findByCode("RH").orElseThrow();
        financeDepartment = departmentRepository.findByCode("FINANCE").orElseThrow();
        itDepartment = departmentRepository.findByCode("IT").orElseThrow();
        // OPERATIONS (sensible, MANAGER/ADMIN uniquement) : totalement inutilise ailleurs dans la
        // suite, isolation complete pour le test de regroupement de citations ci-dessous.
        operationsDepartment = departmentRepository.findByCode("OPERATIONS").orElseThrow();
    }

    @Test
    void rejectsQuestionWithoutAuthentication() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Quelle est la politique de conges ?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeAsksQuestionWithinScope_returnsAnswerWithCitations() throws Exception {
        seedChunk(rhDepartment.getId(), "Politique de conges", "conges.pdf", "Chaque salarie dispose de 25 jours de conges payes par an.");
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());

        // departmentId est fixe explicitement a RH : sans cela, le perimetre complet de
        // l'employe (RH+IT+GENERAL) inclurait aussi GENERAL, pollue par les chunks reels
        // d'autres tests d'integration (IngestionPipelineIntegrationTest, ChunkSearchRepository
        // IntegrationTest), qui peuvent evincer ce chunk du top-K.
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"question\":\"Combien de jours de conges par an ?\",\"departmentId\":"
                                + rhDepartment.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.answer").value("Reponse generee (test) [1]."))
                .andExpect(jsonPath("$.sources[?(@.documentTitle == 'Politique de conges')]").exists());
    }

    @Test
    void literalBracesInQuestionAndRetrievedContentDoNotBreakTheChatClient() throws Exception {
        // ChatClient.system(String)/.user(String) passent par defaut par un TemplateRenderer
        // StringTemplate ("{}" = expression de template) avant d'atteindre le modele : un extrait
        // de document contenant une accolade suivie d'un identifiant qui ressemble a une variable
        // de template (ex. "{count}") est le cas le plus dangereux - il pourrait etre supprime
        // silencieusement (substitution vers une variable non liee) plutot que de faire planter
        // la requete, ce qui ne serait detecte par aucun test verifiant seulement le code HTTP.
        // On capture donc le Prompt reellement envoye au ChatModel pour verifier qu'il n'a PAS ete
        // altere.
        String chunkContent = "Le quota par defaut est de {count} requetes par jour et par utilisateur.";
        seedChunk(rhDepartment.getId(), "Configuration technique", "config.json", chunkContent);
        com.tewendelabs.airag.dto.ChatRequest request = new com.tewendelabs.airag.dto.ChatRequest(
                "Quel est le quota { \"actuel\": true } pour un utilisateur ?", rhDepartment.getId());
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false));

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String systemText = promptCaptor.getValue().getSystemMessage().getText();
        assertThat(systemText).contains(chunkContent);
        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .contains("{ \"actuel\": true }");
    }

    @Test
    void multipleChunksFromSameDocumentAreMergedIntoOneCitation() throws Exception {
        // Phase 6 (CitationBuilder) : plusieurs chunks du MEME document doivent produire UNE
        // seule citation numerotee, pas une par chunk. La chaine du systeme prompt captee prouve
        // aussi que le numero vu par le modele correspond a celui renvoye au client.
        seedDocumentWithChunks(operationsDepartment.getId(), "Procedure de securite", "securite.pdf",
                "Premiere partie de la procedure.", "Deuxieme partie de la procedure.");
        String token = jwtService.generateToken(createUser("MANAGER").getId());

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"question\":\"Quelle est la procedure ?\",\"departmentId\":"
                                + operationsDepartment.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].documentTitle").value("Procedure de securite"))
                .andExpect(jsonPath("$.sources[0].referenceNumber").value(1));

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String systemText = promptCaptor.getValue().getSystemMessage().getText();
        assertThat(systemText).contains("[1] Premiere partie de la procedure.\nDeuxieme partie de la procedure.");
    }

    private void seedDocumentWithChunks(Integer departmentId, String documentTitle, String filename,
            String... chunkContents) {
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (id, department_id, filename, title)
                VALUES (?, ?, ?, ?)
                """, documentId, departmentId, filename, documentTitle);
        float[] embedding = new float[1536];
        embedding[0] = 1.0f;
        for (int i = 0; i < chunkContents.length; i++) {
            chunkSearchRepository.insertChunk(UUID.randomUUID(), documentId, departmentId, null, chunkContents[i],
                    null, i, null, embedding);
        }
    }

    @Test
    void employeeRequestingOutOfScopeDepartment_refusesGracefullyWithoutHttpError() throws Exception {
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"question\":\"Quel est le budget prevu ?\",\"departmentId\":" + financeDepartment.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.refusalReason").value("DEPARTMENT_FORBIDDEN"));

        verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void sensitiveTopicQuestion_refusesWithoutCallingChatModel() throws Exception {
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"question\":\"Quel est le salaire de Jean Dupont ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.refusalReason").value("SENSITIVE_TOPIC"));

        verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void noDocumentsInRequestedDepartment_refusesWithNoRelevantContext() throws Exception {
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"question\":\"Comment configurer mon poste ?\",\"departmentId\":" + itDepartment.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.refusalReason").value("NO_RELEVANT_CONTEXT"));
    }

    @Test
    void everyOutcomeIsPersistedToQueryLog() throws Exception {
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());
        long before = queryLogRepository.count();

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"question\":\"Quel est le salaire de Jean Dupont ?\"}"))
                .andExpect(status().isOk());

        assertThat(queryLogRepository.count()).isEqualTo(before + 1);
    }

    private void seedChunk(Integer departmentId, String documentTitle, String filename, String content) {
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (id, department_id, filename, title)
                VALUES (?, ?, ?, ?)
                """, documentId, departmentId, filename, documentTitle);
        float[] embedding = new float[1536];
        embedding[0] = 1.0f;
        chunkSearchRepository.insertChunk(UUID.randomUUID(), documentId, departmentId, null, content, null, 0, 1,
                embedding);
    }

    private User createUser(String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User user = User.builder()
                .email(roleCode.toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .fullName("Test " + roleCode)
                .role(role)
                .build();
        return userRepository.save(user);
    }
}
