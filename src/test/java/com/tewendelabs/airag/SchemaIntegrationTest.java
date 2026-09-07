package com.tewendelabs.airag;

import org.junit.jupiter.api.Test;

class SchemaIntegrationTest extends AbstractIntegrationTest {

    @Test
    void contextLoadsAndSchemaIsValid() {
        // Le demarrage du contexte suffit : Flyway rejoue V1->V4 sur pgvector/pgvector:pg16,
        // puis Hibernate valide (ddl-auto=validate) que chaque entite JPA correspond au schema
        // reel. Un echec ici indique un decalage entite/colonne introduit en Phase 1.
    }
}
