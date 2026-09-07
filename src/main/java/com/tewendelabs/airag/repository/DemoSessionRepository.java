package com.tewendelabs.airag.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tewendelabs.airag.entity.DemoSession;

@Repository
public interface DemoSessionRepository extends JpaRepository<DemoSession, UUID> {

    List<DemoSession> findByExpiresAtBefore(LocalDateTime cutoff);

    /**
     * UPDATE conditionnel atomique : la verification du plafond et l'incrementation se font dans
     * la meme requete, pour eviter qu'un lire-puis-ecrire en deux etapes ne laisse passer deux
     * appels concurrents au-dela du quota. 0 ligne affectee = quota deja atteint.
     */
    @Modifying
    @Query(value = "UPDATE demo_sessions SET questions_used = questions_used + 1 "
            + "WHERE id = :id AND questions_used < :max", nativeQuery = true)
    int incrementQuestionsUsedIfUnderLimit(@Param("id") UUID id, @Param("max") int max);

    @Modifying
    @Query(value = "UPDATE demo_sessions SET documents_used = documents_used + 1 "
            + "WHERE id = :id AND documents_used < :max", nativeQuery = true)
    int incrementDocumentsUsedIfUnderLimit(@Param("id") UUID id, @Param("max") int max);
}
