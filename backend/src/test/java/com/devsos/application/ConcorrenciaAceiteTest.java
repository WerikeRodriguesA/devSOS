package com.devsos.application;

import com.devsos.BaseIntegrationTest;
import com.devsos.application.dto.session.SessionCreateRequestDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.application.service.SessionService;
import com.devsos.domain.post.PostEntity;
import com.devsos.domain.post.PostTipo;
import com.devsos.domain.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
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
 * Issue #12 — Concorrência do "duplo aceite": N helpers tentam aceitar o MESMO
 * post ao mesmo tempo. Exatamente 1 vence; os demais recebem 400 (regra do
 * service) ou 409 (índice único {@code uniq_sessions_post_active}), nunca outro
 * erro.
 */
class ConcorrenciaAceiteTest extends BaseIntegrationTest {

    private static final int N_HELPERS = 8;

    @Autowired
    private SessionService sessionService;

    @Test
    void apenasUmHelperVenceOAceiteConcorrente() throws Exception {
        UserEntity autor = userRepository.saveAndFlush(new UserEntity(
            "Autor Concorrencia", UUID.randomUUID() + "@teste.com", "gh-autor-" + UUID.randomUUID(), "hash"));
        PostEntity post = postRepository.saveAndFlush(PostEntity.criar(
            autor, "Deadlock sob concorrencia",
            "Varios helpers tentam aceitar este post ao mesmo tempo.",
            "", List.of("java"), PostTipo.FREE, BigDecimal.ZERO));

        UUID[] helperIds = new UUID[N_HELPERS];
        for (int i = 0; i < N_HELPERS; i++) {
            helperIds[i] = userRepository.saveAndFlush(new UserEntity(
                "Helper " + i, "helper-" + i + "-" + UUID.randomUUID() + "@teste.com",
                "gh-helper-" + i + "-" + UUID.randomUUID(), "hash")).getId();
        }

        ExecutorService pool = Executors.newFixedThreadPool(N_HELPERS);
        CountDownLatch disparo = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger regra400 = new AtomicInteger();
        AtomicInteger integridade409 = new AtomicInteger();
        AtomicInteger outros = new AtomicInteger();

        for (UUID helperId : helperIds) {
            pool.submit(() -> {
                try {
                    disparo.await();
                    sessionService.aceitarSocorro(helperId, new SessionCreateRequestDTO(post.getId()));
                    sucessos.incrementAndGet();
                } catch (InterruptedException e) {
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

        System.out.println("[concorrencia-aceite] helpers=" + N_HELPERS
            + " vencedores=" + sucessos.get()
            + " 400=" + regra400.get()
            + " 409=" + integridade409.get()
            + " outros=" + outros.get());

        assertEquals(1, sucessos.get(), "exatamente UM helper deve vencer");
        assertEquals(N_HELPERS - 1, regra400.get() + integridade409.get(),
            "os demais devem falhar com 400 ou 409");
        assertEquals(0, outros.get(), "nenhum erro fora do esperado");
    }
}