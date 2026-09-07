package com.tewendelabs.airag.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.embedding.EmbeddingModel;

import com.tewendelabs.airag.config.RagCostProperties;
import com.tewendelabs.airag.config.RagProperties;
import com.tewendelabs.airag.dto.ChatAnswerResponse;
import com.tewendelabs.airag.dto.ChatRequest;
import com.tewendelabs.airag.exceptions.InvalidChatRequestException;
import com.tewendelabs.airag.entity.QueryLog;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.rag.guard.DepartmentAccessGuard;
import com.tewendelabs.airag.rag.guard.NoContextGuard;
import com.tewendelabs.airag.rag.guard.QuotaGuard;
import com.tewendelabs.airag.rag.guard.RagRefusalException;
import com.tewendelabs.airag.rag.guard.RefusalReason;
import com.tewendelabs.airag.rag.guard.SensitiveTopicGuard;
import com.tewendelabs.airag.repository.ChunkSearchRepository;
import com.tewendelabs.airag.repository.ChunkSearchResult;
import com.tewendelabs.airag.repository.QueryLogRepository;
import com.tewendelabs.airag.repository.UserRepository;
import com.tewendelabs.airag.service.DemoSessionService;

class RagQueryServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final DemoSessionService demoSessionService = mock(DemoSessionService.class);
    private final SensitiveTopicGuard sensitiveTopicGuard = mock(SensitiveTopicGuard.class);
    private final DepartmentAccessGuard departmentAccessGuard = mock(DepartmentAccessGuard.class);
    private final QuotaGuard quotaGuard = mock(QuotaGuard.class);
    private final NoContextGuard noContextGuard = mock(NoContextGuard.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final ChunkSearchRepository chunkSearchRepository = mock(ChunkSearchRepository.class);
    // Instance reelle (pas un mock) : CitationBuilder est une fonction pure sans dependance, la
    // mocker retirerait toute verification reelle du regroupement/numerotation des citations.
    private final CitationBuilder citationBuilder = new CitationBuilder();
    private final PromptBuilder promptBuilder = mock(PromptBuilder.class);
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
    private final QueryLogRepository queryLogRepository = mock(QueryLogRepository.class);
    private final RagProperties ragProperties = new RagProperties(5, 0.5);
    // Prix ronds pour que le cout attendu dans les tests se calcule de tete : 1$/1k tokens prompt,
    // 2$/1k tokens completion.
    private final RagCostProperties ragCostProperties = new RagCostProperties(1.0, 2.0);

    private final RagQueryService service = new RagQueryService(userRepository, demoSessionService,
            sensitiveTopicGuard, departmentAccessGuard, quotaGuard, noContextGuard, embeddingModel,
            chunkSearchRepository, citationBuilder, promptBuilder, chatClient, queryLogRepository, ragProperties,
            ragCostProperties);

    private final User user = User.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void stubUserLookup() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private void stubChatClientChain(String content) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        // RagQueryService n'appelle que .chatResponse() (jamais .content() en plus) : la chaine
        // de garde-fous d'un CallResponseSpec reel est a usage unique, un deuxieme accesseur la
        // rejouerait a vide. Voir le commentaire equivalent dans RagQueryService.
        Generation generation = new Generation(new AssistantMessage(content));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(new DefaultUsage(50, 20)).build();
        when(callResponseSpec.chatResponse()).thenReturn(new ChatResponse(List.of(generation), metadata));
    }

    @Test
    void blankQuestion_isRejectedBeforeAnyGuardRuns() {
        assertThatThrownBy(() -> service.answer(user.getId(), new ChatRequest("   ", null)))
                .isInstanceOf(InvalidChatRequestException.class);

        verifyNoInteractions(sensitiveTopicGuard, departmentAccessGuard, embeddingModel, chunkSearchRepository);
    }

    @Test
    void happyPath_returnsAnswerWithCitationsAndPersistsSuccessfulLog() {
        when(departmentAccessGuard.resolveScope(user, null)).thenReturn(List.of(1, 2));
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] { 1f }));
        ChunkSearchResult chunk = new ChunkSearchResult(UUID.randomUUID(), UUID.randomUUID(), "Politique RH",
                "rh.pdf", null, 0, 3, "Contenu pertinent", 0.9);
        when(chunkSearchRepository.searchByDepartments(any(), eq(List.of(1, 2)), anyInt()))
                .thenReturn(List.of(chunk));
        when(promptBuilder.buildSystemPrompt(anyList())).thenReturn("system prompt");
        stubChatClientChain("Voici la reponse [1].");

        ChatAnswerResponse response = service.answer(user.getId(), new ChatRequest("Quelle est la politique ?", null));

        assertThat(response.refused()).isFalse();
        assertThat(response.answer()).isEqualTo("Voici la reponse [1].");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).documentTitle()).isEqualTo("Politique RH");
        assertThat(response.sources().get(0).pageNumber()).isEqualTo(3);

        var captor = org.mockito.ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(captor.capture());
        assertThat(captor.getValue().isAccessAllowed()).isTrue();
        assertThat(captor.getValue().getRefusalReason()).isNull();
        // stubChatClientChain simule Usage(50 prompt, 20 completion) ; ragCostProperties fixe
        // 1$/1k prompt + 2$/1k completion => 50*0.001 + 20*0.002 = 0.09.
        assertThat(captor.getValue().getTokensUsed()).isEqualTo(70);
        assertThat(captor.getValue().getEstimatedCostUsd())
                .isEqualByComparingTo(new java.math.BigDecimal("0.090000"));
    }

    @Test
    void sensitiveTopicRefusal_shortCircuitsBeforeEmbeddingAndSearch() {
        doThrow(new RagRefusalException(RefusalReason.SENSITIVE_TOPIC))
                .when(sensitiveTopicGuard).check(anyString());

        ChatAnswerResponse response = service.answer(user.getId(),
                new ChatRequest("Quel est le salaire de Jean Dupont ?", null));

        assertThat(response.refused()).isTrue();
        assertThat(response.refusalReason()).isEqualTo(RefusalReason.SENSITIVE_TOPIC);
        verifyNoInteractions(embeddingModel, chunkSearchRepository, chatClient);
        verify(queryLogRepository).save(argThat(log -> log != null && !log.isAccessAllowed()));
    }

    @Test
    void departmentForbiddenRefusal_shortCircuitsBeforeEmbedding() {
        when(departmentAccessGuard.resolveScope(user, 99))
                .thenThrow(new RagRefusalException(RefusalReason.DEPARTMENT_FORBIDDEN));

        ChatAnswerResponse response = service.answer(user.getId(), new ChatRequest("Une question", 99));

        assertThat(response.refused()).isTrue();
        assertThat(response.refusalReason()).isEqualTo(RefusalReason.DEPARTMENT_FORBIDDEN);
        verifyNoInteractions(embeddingModel, chatClient);
    }

    @Test
    void noContextRefusal_neverCallsChatClient() {
        when(departmentAccessGuard.resolveScope(user, null)).thenReturn(List.of(1));
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] { 1f }));
        when(chunkSearchRepository.searchByDepartments(any(), eq(List.of(1)), anyInt())).thenReturn(List.of());
        doThrow(new RagRefusalException(RefusalReason.NO_RELEVANT_CONTEXT))
                .when(noContextGuard).check(any(), anyDouble());

        ChatAnswerResponse response = service.answer(user.getId(), new ChatRequest("Une question", null));

        assertThat(response.refused()).isTrue();
        assertThat(response.refusalReason()).isEqualTo(RefusalReason.NO_RELEVANT_CONTEXT);
        verify(chatClient, never()).prompt();
    }

    @Test
    void answerAsDemo_happyPath_returnsAnswerAndPersistsLogAgainstSession() {
        UUID sessionId = UUID.randomUUID();
        com.tewendelabs.airag.entity.DemoSession session = com.tewendelabs.airag.entity.DemoSession.builder()
                .id(sessionId)
                .expiresAt(java.time.LocalDateTime.now().plusHours(1))
                .build();
        when(demoSessionService.findExisting(sessionId)).thenReturn(session);
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] { 1f }));
        ChunkSearchResult chunk = new ChunkSearchResult(UUID.randomUUID(), UUID.randomUUID(), "Doc demo",
                "demo.pdf", null, 0, null, "Contenu", 0.9);
        when(chunkSearchRepository.searchByDemoSession(any(), eq(sessionId), anyInt())).thenReturn(List.of(chunk));
        when(promptBuilder.buildSystemPrompt(anyList())).thenReturn("system prompt");
        stubChatClientChain("Reponse demo [1].");

        ChatAnswerResponse response = service.answerAsDemo(sessionId, new ChatRequest("Une question", null));

        assertThat(response.refused()).isFalse();
        assertThat(response.answer()).isEqualTo("Reponse demo [1].");
        verify(quotaGuard).checkAndConsume(session);

        var captor = org.mockito.ArgumentCaptor.forClass(QueryLog.class);
        verify(queryLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getDemoSessionId()).isEqualTo(sessionId);
        assertThat(captor.getValue().isAccessAllowed()).isTrue();
    }

    @Test
    void answerAsDemo_quotaExceeded_persistsLogThenThrows429Exception() {
        UUID sessionId = UUID.randomUUID();
        com.tewendelabs.airag.entity.DemoSession session = com.tewendelabs.airag.entity.DemoSession.builder()
                .id(sessionId)
                .expiresAt(java.time.LocalDateTime.now().plusHours(1))
                .build();
        when(demoSessionService.findExisting(sessionId)).thenReturn(session);
        doThrow(new RagRefusalException(RefusalReason.QUOTA_EXCEEDED))
                .when(quotaGuard).checkAndConsume(session);

        assertThatThrownBy(() -> service.answerAsDemo(sessionId, new ChatRequest("Une question", null)))
                .isInstanceOf(com.tewendelabs.airag.exceptions.DemoQuotaExceededException.class);

        verify(queryLogRepository).save(argThat(log -> log != null && !log.isAccessAllowed()
                && "QUOTA_EXCEEDED".equals(log.getRefusalReason()) && sessionId.equals(log.getDemoSessionId())));
        verifyNoInteractions(embeddingModel, chatClient);
    }

    @Test
    void answerAsDemo_sessionExpired_returnsGracefulRefusalInsteadOfError() {
        UUID sessionId = UUID.randomUUID();
        com.tewendelabs.airag.entity.DemoSession session = com.tewendelabs.airag.entity.DemoSession.builder()
                .id(sessionId)
                .expiresAt(java.time.LocalDateTime.now().minusMinutes(1))
                .build();
        when(demoSessionService.findExisting(sessionId)).thenReturn(session);
        doThrow(new RagRefusalException(RefusalReason.SESSION_EXPIRED))
                .when(quotaGuard).checkAndConsume(session);

        ChatAnswerResponse response = service.answerAsDemo(sessionId, new ChatRequest("Une question", null));

        assertThat(response.refused()).isTrue();
        assertThat(response.refusalReason()).isEqualTo(RefusalReason.SESSION_EXPIRED);
        verifyNoInteractions(embeddingModel, chatClient);
    }
}
