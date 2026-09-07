package com.tewendelabs.airag.ingestion;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tewendelabs.airag.entity.Document;
import com.tewendelabs.airag.repository.ChunkSearchRepository;

/**
 * Ecrit les chunks embeddes via {@link ChunkSearchRepository} (JDBC natif, cf. decision
 * architecturale sur le bypass du VectorStore Spring AI). {@code section_label} et
 * {@code page_number} au niveau chunk restent null dans ce scope : aucune correlation
 * position-dans-le-texte / page n'est calculee (TextExtractionService ne fournit qu'un nombre de
 * pages global pour le document, pas de decoupage par page).
 */
@Service
public class ChunkPersistenceWriter {

    private final ChunkSearchRepository chunkSearchRepository;

    public ChunkPersistenceWriter(ChunkSearchRepository chunkSearchRepository) {
        this.chunkSearchRepository = chunkSearchRepository;
    }

    public void persist(Document document, List<EmbeddedChunk> embeddedChunks) {
        Integer departmentId = document.getDepartment() != null ? document.getDepartment().getId() : null;
        UUID demoSessionId = document.getDemoSessionId();

        for (EmbeddedChunk embedded : embeddedChunks) {
            chunkSearchRepository.insertChunk(
                    UUID.randomUUID(),
                    document.getId(),
                    departmentId,
                    demoSessionId,
                    embedded.chunk().content(),
                    null,
                    embedded.chunk().index(),
                    null,
                    embedded.embedding());
        }
    }
}
