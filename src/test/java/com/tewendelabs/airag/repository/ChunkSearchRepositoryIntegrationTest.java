package com.tewendelabs.airag.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.tewendelabs.airag.AbstractIntegrationTest;

class ChunkSearchRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ChunkSearchRepository chunkSearchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertsAndFindsChunkWithinAuthorizedDepartments() {
        Integer departmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM departments WHERE code = 'GENERAL'", Integer.class);
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (id, department_id, filename, title)
                VALUES (?, ?, ?, ?)
                """, documentId, departmentId, "politique-generale.pdf", "Politique generale");

        float[] embedding = new float[1536];
        embedding[0] = 1.0f;
        UUID chunkId = UUID.randomUUID();
        chunkSearchRepository.insertChunk(chunkId, documentId, departmentId, null,
                "Contenu de test", "Section 1", 0, 1, embedding);

        List<ChunkSearchResult> results = chunkSearchRepository.searchByDepartments(
                embedding, List.of(departmentId), 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(chunkId);
        assertThat(results.get(0).similarity()).isGreaterThan(0.99);
    }

    @Test
    void doesNotReturnChunksOutsideAuthorizedDepartments() {
        Integer generalId = jdbcTemplate.queryForObject(
                "SELECT id FROM departments WHERE code = 'GENERAL'", Integer.class);
        Integer financeId = jdbcTemplate.queryForObject(
                "SELECT id FROM departments WHERE code = 'FINANCE'", Integer.class);
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (id, department_id, filename, title)
                VALUES (?, ?, ?, ?)
                """, documentId, financeId, "budget.pdf", "Budget");

        float[] embedding = new float[1536];
        embedding[0] = 1.0f;
        chunkSearchRepository.insertChunk(UUID.randomUUID(), documentId, financeId, null,
                "Donnees financieres confidentielles", "Section 1", 0, 1, embedding);

        List<ChunkSearchResult> results = chunkSearchRepository.searchByDepartments(
                embedding, List.of(generalId), 5);

        assertThat(results).isEmpty();
    }

    @Test
    void searchesByDemoSessionScope() {
        UUID demoSessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO demo_sessions (id, ip_address, expires_at)
                VALUES (?, ?, now() + interval '1 hour')
                """, demoSessionId, "127.0.0.1");
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (id, demo_session_id, filename, title)
                VALUES (?, ?, ?, ?)
                """, documentId, demoSessionId, "upload.pdf", "Upload demo");

        float[] embedding = new float[1536];
        embedding[0] = 1.0f;
        UUID chunkId = UUID.randomUUID();
        chunkSearchRepository.insertChunk(chunkId, documentId, null, demoSessionId,
                "Contenu demo", "Section 1", 0, null, embedding);

        List<ChunkSearchResult> results = chunkSearchRepository.searchByDemoSession(
                embedding, demoSessionId, 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(chunkId);
    }
}
