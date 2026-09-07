package com.tewendelabs.airag.repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.pgvector.PGvector;


@Repository
public class ChunkSearchRepository {

    private static final String SELECT_COLUMNS = """
            c.id, c.document_id, d.title, d.filename, c.section_label, c.chunk_index,
            c.page_number, c.content, (c.embedding <=> ?) AS distance""";

    private final JdbcTemplate jdbcTemplate;

    public ChunkSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Mode authentifie : perimetre = departements autorises par le role de l'utilisateur. */
    public List<ChunkSearchResult> searchByDepartments(float[] queryEmbedding, List<Integer> departmentIds,
            int topK) {
        if (departmentIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT " + SELECT_COLUMNS + """

                FROM chunks c JOIN documents d ON d.id = c.document_id
                WHERE c.department_id = ANY (?)
                ORDER BY c.embedding <=> ?
                LIMIT ?""";
        PGvector vector = new PGvector(queryEmbedding);
        Integer[] ids = departmentIds.toArray(new Integer[0]);
        return jdbcTemplate.query(sql, ps -> {
            PGvector.addVectorType(ps.getConnection());
            Array sqlArray = ps.getConnection().createArrayOf("integer", ids);
            ps.setObject(1, vector);
            ps.setArray(2, sqlArray);
            ps.setObject(3, vector);
            ps.setInt(4, topK);
        }, ChunkSearchRepository::mapRow);
    }

    /** Mode demo : perimetre = uniquement les documents uploades par cette session anonyme. */
    public List<ChunkSearchResult> searchByDemoSession(float[] queryEmbedding, UUID demoSessionId, int topK) {
        String sql = "SELECT " + SELECT_COLUMNS + """

                FROM chunks c JOIN documents d ON d.id = c.document_id
                WHERE c.demo_session_id = ?
                ORDER BY c.embedding <=> ?
                LIMIT ?""";
        PGvector vector = new PGvector(queryEmbedding);
        return jdbcTemplate.query(sql, ps -> {
            PGvector.addVectorType(ps.getConnection());
            ps.setObject(1, vector);
            ps.setObject(2, demoSessionId);
            ps.setObject(3, vector);
            ps.setInt(4, topK);
        }, ChunkSearchRepository::mapRow);
    }

    public void insertChunk(UUID id, UUID documentId, Integer departmentId, UUID demoSessionId, String content,
            String sectionLabel, int chunkIndex, Integer pageNumber, float[] embedding) {
        String sql = """
                INSERT INTO chunks (id, document_id, department_id, demo_session_id, content,
                                     section_label, chunk_index, page_number, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        jdbcTemplate.update(sql, ps -> {
            PGvector.addVectorType(ps.getConnection());
            ps.setObject(1, id);
            ps.setObject(2, documentId);
            setNullableInt(ps, 3, departmentId);
            setNullableUuid(ps, 4, demoSessionId);
            ps.setString(5, content);
            ps.setString(6, sectionLabel);
            ps.setInt(7, chunkIndex);
            setNullableInt(ps, 8, pageNumber);
            ps.setObject(9, new PGvector(embedding));
        });
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setNullableUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static ChunkSearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        double distance = rs.getDouble("distance");
        return new ChunkSearchResult(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("document_id"),
                rs.getString("title"),
                rs.getString("filename"),
                rs.getString("section_label"),
                rs.getInt("chunk_index"),
                (Integer) rs.getObject("page_number"),
                rs.getString("content"),
                1.0 - distance);
    }
}
