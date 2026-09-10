package com.tewendelabs.airag.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tewendelabs.airag.dto.ObservabilitySummaryResponse;
import com.tewendelabs.airag.service.AdminObservabilityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/observability")
@Tag(name = "Observabilite (admin)", description = "Metriques agregees sur query_logs : volume, refus, "
        + "latence, cout estime. Reserve au role ADMIN.")
public class AdminObservabilityController {

    private final AdminObservabilityService adminObservabilityService;

    public AdminObservabilityController(AdminObservabilityService adminObservabilityService) {
        this.adminObservabilityService = adminObservabilityService;
    }

    @Operation(summary = "Resume d'observabilite", description = "Agrege tout le trafic RAG (demo et "
            + "authentifie confondus) des N derniers jours. Reserve aux ADMIN (403 sinon).")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public ObservabilitySummaryResponse summary(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(value = "days", defaultValue = "30") int days) {
        return adminObservabilityService.getSummary(userId, days);
    }
}
