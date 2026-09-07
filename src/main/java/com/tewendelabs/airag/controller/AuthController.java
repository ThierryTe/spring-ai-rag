package com.tewendelabs.airag.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tewendelabs.airag.dto.AuthMeResponse;
import com.tewendelabs.airag.dto.LoginRequest;
import com.tewendelabs.airag.dto.LoginResponse;
import com.tewendelabs.airag.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Connexion et emission de jetons JWT")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Se connecter", description = "Verifie les identifiants et retourne un jeton JWT valide "
            + "480 minutes.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    @Operation(summary = "Profil courant", description = "Role et perimetre departements de l'utilisateur "
            + "authentifie, a interroger apres login puisque le JWT ne porte que son identifiant.")
    @GetMapping("/me")
    public AuthMeResponse me(@AuthenticationPrincipal UUID userId) {
        return authService.me(userId);
    }
}
