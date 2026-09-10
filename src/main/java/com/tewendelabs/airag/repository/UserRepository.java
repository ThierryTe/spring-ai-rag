package com.tewendelabs.airag.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tewendelabs.airag.entity.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    /**
     * JOIN FETCH le role dans la meme requete : JwtAuthenticationFilter s'execute avant l'ouverture
     * de session Open-In-View (qui demarre au niveau des HandlerInterceptor de DispatcherServlet,
     * pas des Filter servlet), donc {@code user.getRole()} y serait sinon un proxy Hibernate
     * inutilisable hors de la transaction de {@code findById}.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.id = :id")
    Optional<User> findByIdWithRole(@Param("id") UUID id);
}
