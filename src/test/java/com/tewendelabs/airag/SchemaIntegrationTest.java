package com.tewendelabs.airag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndSchemaIsValid() {
        // Flyway applique les migrations et Hibernate valide les mappings au demarrage.
        // Verifier aussi que les tables attendues existent dans le schema reel.
        assertThat(jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, String.class))
                .contains("departments", "roles", "users", "role_permissions",
                        "demo_sessions", "documents", "chunks", "query_logs");
    }
}
