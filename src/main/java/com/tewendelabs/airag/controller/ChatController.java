package com.tewendelabs.airag.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tewendelabs.airag.dto.ChatAnswerResponse;
import com.tewendelabs.airag.dto.ChatRequest;
import com.tewendelabs.airag.rag.RagQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat RAG", description = "Questions/reponses avec citations et garde-fous anti-hallucination")
public class ChatController {

    private final RagQueryService ragQueryService;

    public ChatController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @Operation(summary = "Poser une question", description = "Recherche par similarite dans les documents "
            + "autorises, puis genere une reponse citee. Peut renvoyer refused=true (200, pas une erreur "
            + "HTTP) si la question est hors sujet sensible, hors perimetre departement, ou sans contexte "
            + "pertinent trouve.")
    @PostMapping
    public ChatAnswerResponse chat(@AuthenticationPrincipal UUID userId, @RequestBody ChatRequest request) {
        return ragQueryService.answer(userId, request);
    }
}
