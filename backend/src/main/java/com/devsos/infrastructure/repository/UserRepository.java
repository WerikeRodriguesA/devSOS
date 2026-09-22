package com.devsos.infrastructure.repository;

import com.devsos.domain.user.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Camada de infraestrutura: repositório de {@link UserEntity}.
 * <p>
 * DESMITIFICANDO: declaramos apenas o MÉTODO que vamos usar. O Spring Data
 * gera a implementação na hora, interpretando o nome do método
 * ({@code findByEmail} → {@code SELECT * FROM users WHERE email = ?}).
 * <p>
 * Herdar de {@link JpaRepository} já nos dá CRUD + paginação por template
 * (é "market standard" em projetos Spring Boot).
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByGithubUsername(String githubUsername);

    boolean existsByEmail(String email);

    /** GitHub em uso POR OUTRO usuário (exclui o próprio — edição de perfil). */
    boolean existsByGithubUsernameAndIdNot(String githubUsername, UUID id);
}