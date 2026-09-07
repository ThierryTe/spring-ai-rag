package com.tewendelabs.airag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.tewendelabs.airag.AbstractIntegrationTest;
import com.tewendelabs.airag.entity.DemoSession;
import com.tewendelabs.airag.repository.ChunkSearchRepository;
import com.tewendelabs.airag.repository.DemoSessionRepository;
import com.tewendelabs.airag.repository.DocumentRepository;
import com.tewendelabs.airag.repository.QueryLogRepository;

/**
 * Valide indirectement la migration V5 : sans {@code ON DELETE CASCADE} sur
 * {@code query_logs.demo_session_id}, la suppression ci-dessous echouerait avec une violation de
 * contrainte FK des qu'un {@code query_log} est rattache a la session.
 *
 * <p>Reactive explicitement le job (desactive par defaut dans {@code src/test/resources/
 * application.properties} pour eviter une purge intempestive pendant les autres tests) : cette
 * classe teste le job lui-meme, elle a donc besoin du bean.</p>
 */
@TestPropertySource(properties = "demo.session.cleanup-enabled=true")
class DemoSessionCleanupJobTest extends AbstractIntegrationTest {

    @Autowired
    private DemoSessionCleanupJob cleanupJob;

    @Autowired
    private DemoSessionRepository demoSessionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChunkSearchRepository chunkSearchRepository;

    @Autowired
    private QueryLogRepository queryLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void expiredSessionWithDocumentChunkAndQueryLog_isFullyCascadeDeleted() {
        DemoSession session = demoSessionRepository.save(DemoSession.builder()
                .ipAddress("127.0.0.1")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build());

        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (id, demo_session_id, filename, title)
                VALUES (?, ?, ?, ?)
                """, documentId, session.getId(), "notes.txt", "Notes demo");
        float[] embedding = new float[1536];
        embedding[0] = 1.0f;
        chunkSearchRepository.insertChunk(UUID.randomUUID(), documentId, null, session.getId(),
                "Contenu demo", null, 0, null, embedding);
        jdbcTemplate.update("""
                INSERT INTO query_logs (id, demo_session_id, question, access_allowed)
                VALUES (?, ?, ?, true)
                """, UUID.randomUUID(), session.getId(), "Une question");

        cleanupJob.cleanupExpiredSessions();

        assertThat(demoSessionRepository.findById(session.getId())).isEmpty();
        assertThat(documentRepository.findById(documentId)).isEmpty();
        Integer remainingLogs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM query_logs WHERE demo_session_id = ?", Integer.class, session.getId());
        assertThat(remainingLogs).isZero();
    }

    @Test
    void nonExpiredSession_isNotDeleted() {
        DemoSession session = demoSessionRepository.save(DemoSession.builder()
                .ipAddress("127.0.0.1")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());

        cleanupJob.cleanupExpiredSessions();

        assertThat(demoSessionRepository.findById(session.getId())).isPresent();
    }
}
