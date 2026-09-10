package com.tewendelabs.airag.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tewendelabs.airag.dto.ChatAnswerResponse;
import com.tewendelabs.airag.dto.ChatRequest;
import com.tewendelabs.airag.entity.Department;
import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.entity.DocumentStatus;
import com.tewendelabs.airag.entity.Role;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.ingestion.IngestionPipeline;
import com.tewendelabs.airag.rag.RagQueryService;
import com.tewendelabs.airag.rag.guard.RefusalReason;
import com.tewendelabs.airag.repository.DepartmentRepository;
import com.tewendelabs.airag.repository.DocumentRepository;
import com.tewendelabs.airag.repository.RoleRepository;
import com.tewendelabs.airag.repository.UserRepository;

/**
 * Eval RAG contre un vrai modele OpenAI (embeddings + chat, pas mockes contrairement au reste de la
 * suite) : detecte les regressions de qualite (retrieval, fidelite, refus), pas seulement de
 * compilation. Exclu par defaut (pom.xml, excludedGroups=eval) et ignore sans cle reelle ; a lancer
 * via {@code mvn test -Dgroups=eval -DexcludedGroups=} avec OPENAI_API_KEY renseignee.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.*")
class RagGoldenEvalTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    private static final String FIXTURE_CONTENT = "Politique de teletravail interne. Les collaborateurs de "
            + "l'entreprise sont autorises a teletravailler jusqu'a 2 jours par semaine, sous reserve de "
            + "l'accord prealable de leur manager direct. Le teletravail s'effectue exclusivement depuis le "
            + "domicile declare aupres des ressources humaines. Toute demande de jour de teletravail "
            + "supplementaire doit etre soumise via le formulaire RH au moins 5 jours ouvres a l'avance et "
            + "reste soumise a validation.";

    @Autowired
    private RagQueryService ragQueryService;

    @Autowired
    private IngestionPipeline ingestionPipeline;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID employeeUserId;

    @BeforeAll
    void seedCorpusAndUser() {
        Department general = departmentRepository.findByCode("GENERAL").orElseThrow();
        Document document = documentRepository.save(Document.builder()
                .department(general)
                .filename("politique-teletravail.txt")
                .status(DocumentStatus.UPLOADED)
                .build());
        ingestionPipeline.process(document.getId(), FIXTURE_CONTENT.getBytes(StandardCharsets.UTF_8));
        await().atMost(Duration.ofSeconds(30)).until(() ->
                documentRepository.findById(document.getId()).orElseThrow().getStatus() == DocumentStatus.INDEXED);

        Role role = roleRepository.findByCode("EMPLOYEE").orElseThrow();
        employeeUserId = userRepository.save(User.builder()
                .email("eval-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("password"))
                .fullName("Eval Bot")
                .role(role)
                .build()).getId();
    }

    static Stream<GoldenCase> goldenCases() throws Exception {
        try (InputStream in = RagGoldenEvalTest.class.getResourceAsStream("/eval/golden-qa.json")) {
            return Stream.of(new ObjectMapper().readValue(in, GoldenCase[].class));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void answerMatchesGoldenExpectation(GoldenCase testCase) {
        ChatAnswerResponse response = ragQueryService.answer(employeeUserId,
                new ChatRequest(testCase.question(), null));

        if (testCase.expectedRefusalReason() != null) {
            assertThat(response.refused()).isTrue();
            assertThat(response.refusalReason()).isEqualTo(RefusalReason.valueOf(testCase.expectedRefusalReason()));
        } else {
            assertThat(response.refused()).isFalse();
            assertThat(response.sources()).isNotEmpty();
            String lowerAnswer = response.answer().toLowerCase(Locale.FRENCH);
            assertThat(testCase.mustContainAny())
                    .anyMatch(keyword -> lowerAnswer.contains(keyword.toLowerCase(Locale.FRENCH)));
        }
    }

    record GoldenCase(String id, String question, String expectedRefusalReason, List<String> mustContainAny) {
        @Override
        public String toString() {
            return id;
        }
    }
}
