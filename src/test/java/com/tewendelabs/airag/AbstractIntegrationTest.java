package com.tewendelabs.airag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base pour les tests d'integration : demarre un vrai PostgreSQL+pgvector (meme image que
 * {@code compose.yaml}), sur lequel Flyway rejoue V1->V4 et Hibernate valide le mapping des
 * entites (ddl-auto=validate) au demarrage du contexte.
 *
 * <p>Le conteneur est demarre une seule fois via un bloc statique, PAS via {@code @Testcontainers}
 * / {@code @Container} : ces annotations arretent puis redemarrent le conteneur a chaque classe de
 * test (nouveau port a chaque redemarrage), alors que le champ statique {@code POSTGRES} et le
 * contexte Spring mis en cache par {@code @SpringBootTest} sont partages entre toutes les classes
 * qui etendent cette base - un thread issu du pool {@code ingestionTaskExecutor} qui emprunte une
 * connexion apres un tel redemarrage se retrouve alors a parler a un port mort ("Connection
 * refused"). {@code @ServiceConnection} n'a pas besoin de {@code @Testcontainers} : c'est un
 * mecanisme Spring independant qui detecte ce champ tant que le conteneur tourne au moment du
 * refresh du contexte.</p>
 *
 * <p>{@code EmbeddingModel} et {@code ChatModel} sont systematiquement mockes ici (et pas
 * seulement dans les tests qui en ont explicitement besoin) : depuis que
 * {@code DocumentService.upload} declenche {@code IngestionPipeline} en arriere-plan et que
 * {@code RagQueryService} appelle le {@code ChatClient} pour /api/chat, n'importe quel test de
 * cette suite qui uploade un document ou pose une question appellerait sinon la vraie API OpenAI
 * (cout, reseau, flakiness) sans meme s'en rendre compte. Le {@code ChatClient} injecte dans
 * {@code RagQueryService} est un vrai bean Spring AI (voir {@code ChatClientConfig}) construit
 * par-dessus ce {@code ChatModel} mocke : {@code ChatClientAutoConfiguration} le recoit en
 * parametre de construction, donc remplacer le bean {@code ChatModel} suffit sans toucher au
 * bean {@code ChatClient} lui-meme (chainage fluent {@code prompt().system().user().call()}
 * difficile a mocker directement).</p>
 */
@SpringBootTest
// DemoSessionCleanupJob (@Scheduled) desactive par defaut : sans cela, son execution au demarrage
// du contexte partage pourrait supprimer, entre le setup et l'assertion d'un test, une session
// demo volontairement creee expiree (voir DemoSessionControllerIntegrationTest). Reactive
// explicitement dans DemoSessionCleanupJobTest, seul test qui exerce le job lui-meme.
@TestPropertySource(properties = "demo.session.cleanup-enabled=false")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @MockitoBean
    protected EmbeddingModel embeddingModel;

    @MockitoBean
    protected ChatModel chatModel;

    @BeforeEach
    void stubEmbeddingModelWithDeterministicVectors() {
        Mockito.lenient().when(embeddingModel.embed(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            // Vecteur non-nul : pgvector rejette la distance cosinus (<=>) sur un vecteur de
            // norme zero ("vector cannot have zero norm"), un vecteur tout-zero ferait donc
            // planter toute recherche vectorielle qui consomme ces embeddings de test.
            return texts.stream().map(text -> {
                float[] vector = new float[1536];
                vector[0] = 1f;
                return vector;
            }).toList();
        });
    }

    @BeforeEach
    void stubChatModelWithDeterministicAnswer() {
        // ChatClientAutoConfiguration lit chatModel.getOptions().mutate() pour construire chaque
        // requete : sans ce stub, le mock renvoie null et toChatClientRequest() leve une NPE
        // avant meme d'atteindre la reponse simulee ci-dessous.
        Mockito.lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        Mockito.lenient().when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Generation generation = new Generation(new AssistantMessage("Reponse generee (test) [1]."));
            ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                    .usage(new DefaultUsage(50, 20))
                    .build();
            return new ChatResponse(List.of(generation), metadata);
        });
    }
}
