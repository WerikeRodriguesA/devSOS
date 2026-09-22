package com.devsos;

import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.PostRepository;
import com.devsos.infrastructure.repository.UserRepository;
import com.devsos.infrastructure.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/**
 * Base da suíte de integração (issue #12).
 *
 * <p>Sobe um <b>Postgres real em container</b> (Testcontainers + Flyway aplica
 * as migrações V1..V3 do zero) e expõe o {@link MockMvc} contra o contexto
 * completo, além de helpers comuns (criar usuário + gerar JWT válido).</p>
 *
 * <p>O container é um <b>bean do contexto</b> ({@link ContainersConfig} com
 * {@code @ServiceConnection}): assim ele vive junto do ApplicationContext
 * cacheado e todas as classes de teste compartilham o MESMO banco/porta — o
 * que não aconteceria com um {@code @Container static}, que é parado no fim de
 * cada classe e reaproveitaria a porta antiga presa no contexto.</p>
 *
 * <p>Não usamos {@code @Transactional} por classe de teste: o controller roda a
 * transação de verdade (e testes de concorrência precisam disso). Cada teste
 * cria os próprios dados com emails/githubs únicos de {@link UUID}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(BaseIntegrationTest.ContainersConfig.class)
public abstract class BaseIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainersConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("devsos_test")
                .withUsername("devsos")
                .withPassword("devsos");
        }
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PostRepository postRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected static final String SENHA_PADRAO = "senha123";

    /** Usuário + JWT prontos para as chamadas autenticadas. */
    protected record UsuarioLei(UUID id, String email, String token) {}

    protected UsuarioLei criarUsuario(String nome, String email, String github) {
        UserEntity user = userRepository.saveAndFlush(
            new UserEntity(nome, email, github, passwordEncoder.encode(SENHA_PADRAO)));
        String token = jwtService.gerarToken(user.getId(), user.getEmail(), user.getNome());
        return new UsuarioLei(user.getId(), user.getEmail(), token);
    }

    protected String bearer(UsuarioLei u) {
        return "Bearer " + u.token();
    }

    /** Credita pontos para o usuário (testes de recompensa PAID). */
    protected void creditarSaldo(UUID userId, int pontos) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        user.setSaldoPontos(pontos);
        userRepository.saveAndFlush(user);
    }

    /** Saldo atual do usuário (lido do banco). */
    protected int saldoDe(UUID userId) {
        return userRepository.findById(userId).orElseThrow().getSaldoPontos();
    }
}