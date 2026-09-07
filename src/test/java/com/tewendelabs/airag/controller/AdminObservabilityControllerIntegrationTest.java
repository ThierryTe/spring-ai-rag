package com.tewendelabs.airag.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.tewendelabs.airag.entity.QueryLog;
import com.tewendelabs.airag.entity.Role;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.repository.QueryLogRepository;
import com.tewendelabs.airag.repository.RoleRepository;
import com.tewendelabs.airag.repository.UserRepository;
import com.tewendelabs.airag.security.JwtService;

@AutoConfigureMockMvc
class AdminObservabilityControllerIntegrationTest extends com.tewendelabs.airag.AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private QueryLogRepository queryLogRepository;

    @Test
    void withoutAuthentication_returnsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/observability/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeIsForbidden() throws Exception {
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());

        mvc.perform(get("/api/admin/observability/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAggregatedSummary() throws Exception {
        // query_logs est partagee par toute la suite d'integration (meme conteneur Postgres,
        // voir AbstractIntegrationTest) : sans ce nettoyage, les compteurs exacts ci-dessous
        // seraient pollues par les logs persistes par ChatControllerIntegrationTest et consorts.
        queryLogRepository.deleteAll();

        User user = createUser("EMPLOYEE");
        User otherUser = createUser("MANAGER");
        seedLog(user.getId(), true, null, 100, 70, "0.090000");
        seedLog(user.getId(), false, "SENSITIVE_TOPIC", 5, null, null);
        // Trafic authentifie de deux utilisateurs distincts : l'agregation n'est pas censee etre
        // scopee a un seul utilisateur, contrairement a findByUserIdOrderByCreatedAtDesc.
        seedLog(otherUser.getId(), false, "DEPARTMENT_FORBIDDEN", 5, null, null);

        String adminToken = jwtService.generateToken(createUser("ADMIN").getId());

        mvc.perform(get("/api/admin/observability/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQueries").value(3))
                .andExpect(jsonPath("$.allowedQueries").value(1))
                .andExpect(jsonPath("$.refusedQueries").value(2))
                .andExpect(jsonPath("$.refusalBreakdown.length()").value(2))
                .andExpect(jsonPath("$.totalTokensUsed").value(70));
    }

    private void seedLog(UUID userId, boolean accessAllowed, String refusalReason, Integer latencyMs,
            Integer tokensUsed, String estimatedCostUsd) {
        QueryLog log = QueryLog.builder()
                .userId(userId)
                .question("Une question")
                .accessAllowed(accessAllowed)
                .refusalReason(refusalReason)
                .answer("Une reponse")
                .latencyMs(latencyMs)
                .tokensUsed(tokensUsed)
                .estimatedCostUsd(estimatedCostUsd != null ? new BigDecimal(estimatedCostUsd) : null)
                .createdAt(LocalDateTime.now())
                .build();
        queryLogRepository.save(log);
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
