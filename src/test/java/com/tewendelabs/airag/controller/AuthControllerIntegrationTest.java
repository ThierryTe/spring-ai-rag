package com.tewendelabs.airag.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.tewendelabs.airag.entity.Role;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.repository.RoleRepository;
import com.tewendelabs.airag.repository.UserRepository;
import com.tewendelabs.airag.security.JwtService;

@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends com.tewendelabs.airag.AbstractIntegrationTest {

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

    @Test
    void meWithoutAuthentication_returnsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meAsEmployee_returnsRoleAndRhItGeneralScope() throws Exception {
        User user = createUser("EMPLOYEE");
        String token = jwtService.generateToken(user.getId());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.roleCode").value("EMPLOYEE"))
                // Seed V2 : EMPLOYEE -> RH, IT, GENERAL (3 departements), pas FINANCE/OPERATIONS.
                .andExpect(jsonPath("$.allowedDepartments.length()").value(3))
                .andExpect(jsonPath("$.allowedDepartments[?(@.code == 'RH')].label").value("Ressources humaines"));
    }

    @Test
    void meAsAdmin_returnsEveryDepartment() throws Exception {
        String token = jwtService.generateToken(createUser("ADMIN").getId());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCode").value("ADMIN"))
                // Seed V3 : ADMIN a acces a tous les departements (5 au total en V2).
                .andExpect(jsonPath("$.allowedDepartments.length()").value(5));
    }

    // Comptes seedes par V6__seed_demo_users.sql : verifie ici que le hash BCrypt colle bien au
    // mot de passe documente pour la demo, pour chacun des 3 roles.
    @Test
    void loginWithEachSeededDemoAccount_succeedsWithDocumentedPassword() throws Exception {
        assertLoginSucceeds("employe.demo@aicompliancecopilot.dev", "EMPLOYEE");
        assertLoginSucceeds("manager.demo@aicompliancecopilot.dev", "MANAGER");
        assertLoginSucceeds("admin.demo@aicompliancecopilot.dev", "ADMIN");
    }

    private void assertLoginSucceeds(String email, String expectedRoleCode) throws Exception {
        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"Demo1234!\"}";
        String responseJson = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(responseJson, "$.token");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roleCode").value(expectedRoleCode));
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
