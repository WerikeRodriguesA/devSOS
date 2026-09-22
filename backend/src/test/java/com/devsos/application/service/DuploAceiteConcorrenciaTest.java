package com.devsos.application.service;

import com.devsos.application.dto.session.SessionCreateRequestDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.domain.post.PostEntity;
import com.devsos.domain.post.PostTipo;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.PostRepository;
import com.devsos.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #13 — Blindar o "Duplo Aceite" sob concorrência.
 *
 * <p>N helpers tentam aceitar o MESMO post ao mesmo tempo. Esperamos:
 * <b>exatamente 1 vence</b>; os demais recebem {@code 400} (regra de negócio —
 * lê o post já {@code IN_PROGRESS} depois do lock pessimista) ou {@code 409}
 * (índice único {@code uniq_sessions_post_active} — cinto de segurança).</p>
 *
 * <p>Depende do banco {@code devsos_test} (perfil {@code test}): o Flyway
 * aplica todas as migrações do zero e cada rodada usa dados com UUIDs novos,
 * limpos no {@code @AfterAll}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DuploAceiteConcorrenciaTest {

    private static final int N_HELPERS = 8;

    @Autowired
    private SessionService sessionService;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID postId;
    private final List<UUID> helperIds = new ArrayList<>();

    @BeforeAll
    void setup() {
        UserEntity autor = userRepository.saveAndFlush(
            new UserEntity("Autor " + UUID.randomUUID(),
                "autor-" + UUID.randomUUID() + "@teste.com",
                "gh-autor-" + UUID.randomUUID(), "hash"));
        PostEntity post = postRepository.saveAndFlush(
            PostEntity.criar(autor, "Deadlock sob concorrencia",
                "Dois helpers tentam aceitar o mesmo post ao mesmo tempo.",
                "", List.of("java"), PostTipo.FREE, BigDecimal.ZERO));
        postId = post.getId();

        for (int i = 0; i < N_HELPERS; i++) {
            UserEntity helper = userRepository.saveAndFlush(
                new UserEntity("Helper " + i,
                    "helper-" + i + "-" + UUID.randomUUID() + "@teste.com",
                    "gh-helper-" + i + "-" + UUID.randomUUID(), "hash"));
            helperIds.add(helper.getId());
        }
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sessions WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);
        for (UUID id : helperIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    @Test
    void duploAceiteExatamenteUmVence() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(N_HELPERS);
        CountDownLatch disparo = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger regra400 = new AtomicInteger(0);
        AtomicInteger integridade409 = new AtomicInteger(0);
        AtomicInteger outros = new AtomicInteger(0);

        for (UUID helperId : helperIds) {
            pool.submit(() -> {
                try {
                    disparo.await();
                    sessionService.aceitarSocorro(helperId,
                        new SessionCreateRequestDTO(postId));
                    sucessos.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    outros.incrementAndGet();
                } catch (RegraDeNegocioException e) {
                    regra400.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    integridade409.incrementAndGet();
                } catch (Exception e) {
                    outros.incrementAndGet();
                }
            });
        }

        disparo.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "teste estourou o tempo");

        System.out.println("\n[duplo-aceite] helpers=" + N_HELPERS
            + " vencedores=" + sucessos.get()
            + " 400(regra/lock)=" + regra400.get()
            + " 409(indice unico)=" + integridade409.get()
            + " outros=" + outros.get() + "\n");

        assertEquals(1, sucessos.get(),
            "exatamente UM helper deve vencer o duplo aceite");
        assertEquals(N_HELPERS - 1, regra400.get() + integridade409.get(),
            "os demais precisam falhar com 400 (regra) ou 409 (índice), sem vazamento");
        assertEquals(0, outros.get(),
            "nenhum erro fora do esperado (400/409) pode acontecer");
    }
}