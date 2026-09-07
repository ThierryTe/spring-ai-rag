package com.tewendelabs.airag.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.tewendelabs.airag.entity.Department;
import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.entity.Role;
import com.tewendelabs.airag.entity.User;
import com.tewendelabs.airag.repository.DepartmentRepository;
import com.tewendelabs.airag.repository.DocumentRepository;
import com.tewendelabs.airag.repository.RoleRepository;
import com.tewendelabs.airag.repository.UserRepository;
import com.tewendelabs.airag.security.JwtService;

@AutoConfigureMockMvc
class DocumentControllerIntegrationTest extends com.tewendelabs.airag.AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Department generalDepartment;
    private Department financeDepartment;

    @BeforeEach
    void setUpDepartments() {
        generalDepartment = departmentRepository.findByCode("GENERAL").orElseThrow();
        financeDepartment = departmentRepository.findByCode("FINANCE").orElseThrow();
    }

    @Test
    void rejectsUploadWithoutAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "contenu".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("departmentId", generalDepartment.getId().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerCanUploadToAuthorizedDepartment() throws Exception {
        String token = jwtService.generateToken(createUser("MANAGER").getId());
        MockMultipartFile file = new MockMultipartFile("file", "politique.txt", "text/plain",
                "Politique generale de l'entreprise".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("departmentId", generalDepartment.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.documentId").exists());
    }

    @Test
    void employeeCannotUpload() throws Exception {
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "contenu".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("departmentId", generalDepartment.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotAccessDocumentOutsideDepartmentScope() throws Exception {
        Document sensitiveDoc = documentRepository.save(Document.builder()
                .department(financeDepartment)
                .filename("budget.txt")
                .status(com.tewendelabs.airag.entity.DocumentStatus.UPLOADED)
                .build());
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());

        mvc.perform(get("/api/documents/{id}", sensitiveDoc.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCanListDocumentsWithinScope() throws Exception {
        Document generalDoc = documentRepository.save(Document.builder()
                .department(generalDepartment)
                .filename("guide-accueil.txt")
                .status(com.tewendelabs.airag.entity.DocumentStatus.UPLOADED)
                .build());
        String token = jwtService.generateToken(createUser("EMPLOYEE").getId());

        mvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + generalDoc.getId() + "')]").exists());
    }

    @Test
    void managerDeletesDocumentAndChunksCascade() throws Exception {
        Document doc = documentRepository.save(Document.builder()
                .department(generalDepartment)
                .filename("obsolete.txt")
                .status(com.tewendelabs.airag.entity.DocumentStatus.UPLOADED)
                .build());
        String token = jwtService.generateToken(createUser("MANAGER").getId());

        mvc.perform(delete("/api/documents/{id}", doc.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(documentRepository.findById(doc.getId())).isEmpty();
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
