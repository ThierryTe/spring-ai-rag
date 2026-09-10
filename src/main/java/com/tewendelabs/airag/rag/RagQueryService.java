package com.tewendelabs.airag.rag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import com.tewendelabs.airag.config.RagCostProperties;
import com.tewendelabs.airag.config.RagProperties;
import com.tewendelabs.airag.dto.ChatAnswerResponse;
import com.tewendelabs.airag.dto.ChatRequest;
import com.tewendelabs.airag.dto.SourceCitation;
import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.entity.QueryLog;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.exceptions.DemoQuotaExceededException;
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
import com.tewendelabs.airag.exceptions.InvalidChatRequestException;
import com.tewendelabs.airag.exceptions.ResourceNotFoundException;


@Service
public class RagQueryService {

    private final UserRepository userRepository;
    private final DemoSessionService demoSessionService;
    private final SensitiveTopicGuard sensitiveTopicGuard;
    private final DepartmentAccessGuard departmentAccessGuard;
    private final QuotaGuard quotaGuard;
    private final NoContextGuard noContextGuard;
    private final EmbeddingModel embeddingModel;
    private final ChunkSearchRepository chunkSearchRepository;
    private final CitationBuilder citationBuilder;
    private final PromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final QueryLogRepository queryLogRepository;
    private final RagProperties ragProperties;
    private final RagCostProperties ragCostProperties;

    public RagQueryService(UserRepository userRepository, DemoSessionService demoSessionService,
            SensitiveTopicGuard sensitiveTopicGuard, DepartmentAccessGuard departmentAccessGuard,
            QuotaGuard quotaGuard, NoContextGuard noContextGuard, EmbeddingModel embeddingModel,
            ChunkSearchRepository chunkSearchRepository, CitationBuilder citationBuilder,
            PromptBuilder promptBuilder, ChatClient chatClient, QueryLogRepository queryLogRepository,
            RagProperties ragProperties, RagCostProperties ragCostProperties) {
        this.userRepository = userRepository;
        this.demoSessionService = demoSessionService;
        this.sensitiveTopicGuard = sensitiveTopicGuard;
        this.departmentAccessGuard = departmentAccessGuard;
        this.quotaGuard = quotaGuard;
        this.noContextGuard = noContextGuard;
        this.embeddingModel = embeddingModel;
        this.chunkSearchRepository = chunkSearchRepository;
        this.citationBuilder = citationBuilder;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.queryLogRepository = queryLogRepository;
        this.ragProperties = ragProperties;
        this.ragCostProperties = ragCostProperties;
    }

    public ChatAnswerResponse answer(UUID userId, ChatRequest request) {
        validateQuestion(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        long start = System.currentTimeMillis();

        try {
            checkDailyQuota(userId);
            sensitiveTopicGuard.check(request.question());
            List<Integer> departmentIds = departmentAccessGuard.resolveScope(user, request.departmentId());

            float[] questionEmbedding = embeddingModel.embed(List.of(request.question())).get(0);
            List<ChunkSearchResult> results = chunkSearchRepository.searchByDepartments(
                    questionEmbedding, departmentIds, ragProperties.topK());
            noContextGuard.check(results, ragProperties.minSimilarity());

            RagResult result = generate(request.question(), results);
            long latencyMs = System.currentTimeMillis() - start;

            persistLog(user.getId(), null, request, true, null, result.answer(), result.sources(),
                    result.tokenUsage(), latencyMs);
            return new ChatAnswerResponse(result.answer(), result.sources(), false, null, latencyMs);
        } catch (RagRefusalException refusal) {
            long latencyMs = System.currentTimeMillis() - start;
            String answer = refusalMessage(refusal.getReason());
            persistLog(user.getId(), null, request, false, refusal.getReason(), answer, List.of(), null, latencyMs);
            if (refusal.getReason() == RefusalReason.QUOTA_EXCEEDED) {
                throw new DemoQuotaExceededException(answer);
            }
            return new ChatAnswerResponse(answer, List.of(), true, refusal.getReason(), latencyMs);
        }
    }

    /** Quota anti-abus par compte (tous les comptes de ce deploiement sont des comptes de demo). */
    private void checkDailyQuota(UUID userId) {
        long usedToday = queryLogRepository.countByUserIdAndCreatedAtAfter(userId, LocalDate.now().atStartOfDay());
        if (usedToday >= ragProperties.dailyQuestionQuotaPerUser()) {
            throw new RagRefusalException(RefusalReason.QUOTA_EXCEEDED);
        }
    }

    /**
     * Mode demo : {@code findExisting} rejette (401) uniquement une session introuvable ; une
     * session expiree n'est PAS rejetee ici, elle est traitee par {@code QuotaGuard} comme un
     * refus RAG gracieux (SESSION_EXPIRED). Le quota de questions epuise est en revanche relance
     * comme une erreur HTTP 429 apres journalisation, conformement a la strategie de tests du plan
     * de conception ("quota depasse -> 429") - seul refus qui n'aboutit pas a une reponse 200.
     */
    public ChatAnswerResponse answerAsDemo(UUID demoSessionId, ChatRequest request) {
        validateQuestion(request);
        DemoSession session = demoSessionService.findExisting(demoSessionId);
        long start = System.currentTimeMillis();

        try {
            quotaGuard.checkAndConsume(session);
            sensitiveTopicGuard.check(request.question());

            float[] questionEmbedding = embeddingModel.embed(List.of(request.question())).get(0);
            List<ChunkSearchResult> results = chunkSearchRepository.searchByDemoSession(
                    questionEmbedding, demoSessionId, ragProperties.topK());
            noContextGuard.check(results, ragProperties.minSimilarity());

            RagResult result = generate(request.question(), results);
            long latencyMs = System.currentTimeMillis() - start;

            persistLog(null, demoSessionId, request, true, null, result.answer(), result.sources(),
                    result.tokenUsage(), latencyMs);
            return new ChatAnswerResponse(result.answer(), result.sources(), false, null, latencyMs);
        } catch (RagRefusalException refusal) {
            long latencyMs = System.currentTimeMillis() - start;
            String answer = refusalMessage(refusal.getReason());
            persistLog(null, demoSessionId, request, false, refusal.getReason(), answer, List.of(), null, latencyMs);
            if (refusal.getReason() == RefusalReason.QUOTA_EXCEEDED) {
                throw new DemoQuotaExceededException(answer);
            }
            return new ChatAnswerResponse(answer, List.of(), true, refusal.getReason(), latencyMs);
        }
    }

    private static void validateQuestion(ChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new InvalidChatRequestException("La question ne peut pas etre vide");
        }
    }

    /** Portion commune aux deux modes : citations -> prompt -> appel LLM -> extraction reponse. */
    private RagResult generate(String question, List<ChunkSearchResult> results) {
        List<CitedContext> citedContexts = citationBuilder.build(results);
        String systemPrompt = promptBuilder.buildSystemPrompt(citedContexts);
        // La chaine de garde-fous d'un ChatClient.CallResponseSpec est a usage unique : que ce
        // soit .content() ou .chatResponse() qui la declenche en premier, le second accesseur
        // rejouerait une chaine deja videe ("No CallAdvisors available to execute"). On n'appelle
        // donc .chatResponse() qu'une seule fois et on en derive le texte.
        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .chatResponse();
        String answer = chatResponse.getResult().getOutput().getText();
        TokenUsage tokenUsage = extractTokenUsage(chatResponse);
        List<SourceCitation> sources = toCitations(citedContexts);
        return new RagResult(answer, sources, tokenUsage);
    }

    private record RagResult(String answer, List<SourceCitation> sources, TokenUsage tokenUsage) {
    }

    /** Prompt et completion sont gardes separes (pas juste le total) : leurs prix different. */
    private record TokenUsage(Integer promptTokens, Integer completionTokens) {
        Integer total() {
            return promptTokens != null && completionTokens != null ? promptTokens + completionTokens : null;
        }
    }

    private static TokenUsage extractTokenUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        return usage != null ? new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens()) : null;
    }

    private BigDecimal estimateCostUsd(TokenUsage tokenUsage) {
        if (tokenUsage == null || tokenUsage.promptTokens() == null || tokenUsage.completionTokens() == null) {
            return null;
        }
        double cost = (tokenUsage.promptTokens() / 1000.0) * ragCostProperties.promptPer1kTokensUsd()
                + (tokenUsage.completionTokens() / 1000.0) * ragCostProperties.completionPer1kTokensUsd();
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }

    private static List<SourceCitation> toCitations(List<CitedContext> citedContexts) {
        return citedContexts.stream()
                .map(cc -> new SourceCitation(cc.referenceNumber(), cc.documentTitle(), cc.filename(),
                        cc.pageNumber(), cc.sectionLabel(), cc.similarityScore()))
                .toList();
    }

    private static String refusalMessage(RefusalReason reason) {
        return switch (reason) {
            case SENSITIVE_TOPIC -> "Je ne peux pas repondre a des questions portant sur des informations "
                    + "personnelles individuelles.";
            case DEPARTMENT_FORBIDDEN -> "Vous n'avez pas acces aux documents de ce departement.";
            case NO_RELEVANT_CONTEXT -> "Je ne dispose pas d'information sur ce sujet dans les documents "
                    + "auxquels vous avez acces.";
            case QUOTA_EXCEEDED -> "Vous avez atteint le nombre maximal de questions autorisees pour cette "
                    + "periode.";
            case SESSION_EXPIRED -> "Votre session demo a expire, veuillez en creer une nouvelle.";
        };
    }

    /** {@code userId} et {@code demoSessionId} sont exclusifs (contrainte DB {@code chk_query_owner}). */
    private void persistLog(UUID userId, UUID demoSessionId, ChatRequest request, boolean accessAllowed,
            RefusalReason refusalReason, String answer, List<SourceCitation> sources, TokenUsage tokenUsage,
            long latencyMs) {
        QueryLog log = QueryLog.builder()
                .userId(userId)
                .demoSessionId(demoSessionId)
                .question(request.question())
                .departmentRequested(request.departmentId())
                .accessAllowed(accessAllowed)
                .refusalReason(refusalReason != null ? refusalReason.name() : null)
                .answer(answer)
                .sourcesCited(sources.stream().map(SourceCitation::documentTitle).toArray(String[]::new))
                .latencyMs((int) latencyMs)
                .tokensUsed(tokenUsage != null ? tokenUsage.total() : null)
                .estimatedCostUsd(estimateCostUsd(tokenUsage))
                .build();
        queryLogRepository.save(log);
    }
}
