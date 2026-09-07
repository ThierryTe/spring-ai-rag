package com.tewendelabs.airag.exceptions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tewendelabs.airag.AbstractIntegrationTest;
import com.tewendelabs.airag.dto.ChatRequest;
import com.tewendelabs.airag.entity.Role;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.rag.RagQueryService;
import com.tewendelabs.airag.repository.RoleRepository;
import com.tewendelabs.airag.repository.UserRepository;
import com.tewendelabs.airag.security.JwtService;

/**
 * Verifie de bout en bout (vrai contexte Spring, vrai filtre JWT, vrai DispatcherServlet) que le
 * filet de securite {@code @ExceptionHandler(Exception.class)} de {@link GlobalExceptionHandler}
 * intercepte bien une exception applicative totalement imprevue - avant l'ajout de ce handler, ce
 * genre d'exception traversait la chaine sans etre attrapee (verifie en le reproduisant lors de
 * l'implementation : {@code ResponseEntityExceptionHandler.handleException} ne couvre qu'une
 * liste fixe de types Spring MVC, pas {@code Exception.class} en general).
 */
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest extends AbstractIntegrationTest {

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

    @MockitoBean
    private RagQueryService ragQueryService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Test
    void unexpectedExceptionReturnsConsistentGenericJsonInsteadOfPropagatingUncaught() throws Exception {
        when(ragQueryService.answer(any(), any())).thenThrow(new RuntimeException("boom - detail interne"));
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());
        ChatRequest request = new ChatRequest("Une question quelconque", null);

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Une erreur inattendue est survenue"))
                .andExpect(jsonPath("$.path").value("/api/chat"));
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
