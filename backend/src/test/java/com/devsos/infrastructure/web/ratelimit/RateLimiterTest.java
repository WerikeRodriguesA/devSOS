package com.devsos.infrastructure.web.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes do núcleo do rate limiting (janela fixa + cooldown progressivo). */
class RateLimiterTest {

    private final RelogioFake relogio = new RelogioFake(Instant.parse("2026-09-21T12:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(relogio, 300);

    @Test
    void permiteAteOLimiteEDepoisBloqueia() {
        assertThat(limiter.tentar("ip-1", 3, 60).permitido()).isTrue();
        assertThat(limiter.tentar("ip-1", 3, 60).permitido()).isTrue();
        assertThat(limiter.tentar("ip-1", 3, 60).permitido()).isTrue();

        RateLimiter.Decisao estouro = limiter.tentar("ip-1", 3, 60);
        assertThat(estouro.permitido()).isFalse();
        assertThat(estouro.restante()).isZero();
        assertThat(estouro.retryAfterSegundos()).isEqualTo(60);
    }

    @Test
    void liberaDeNovoDepoisDoCooldown() {
        for (int i = 0; i < 2; i++) {
            assertThat(limiter.tentar("ip-2", 2, 60).permitido()).isTrue();
        }
        assertThat(limiter.tentar("ip-2", 2, 60).permitido()).isFalse();

        relogio.avancar(Duration.ofSeconds(61));

        assertThat(limiter.tentar("ip-2", 2, 60).permitido()).isTrue();
    }

    @Test
    void cooldownCresceAoCadaNovoEstouro() {
        assertThat(limiter.tentar("ip-3", 1, 60).permitido()).isTrue();

        long primeiro = limiter.tentar("ip-3", 1, 60).retryAfterSegundos();
        assertThat(primeiro).isEqualTo(60);

        // passou o cooldown, mas os strikes continuam: novo estouro dobra.
        relogio.avancar(Duration.ofSeconds(61));
        assertThat(limiter.tentar("ip-3", 1, 60).permitido()).isTrue();
        long segundo = limiter.tentar("ip-3", 1, 60).retryAfterSegundos();

        assertThat(segundo).isEqualTo(120);
    }

    @Test
    void cooldownRespeitaOTetoMaximo() {
        RateLimiter limitado = new RateLimiter(relogio, 90);
        assertThat(limitado.tentar("ip-4", 1, 60).permitido()).isTrue();

        assertThat(limitado.tentar("ip-4", 1, 60).retryAfterSegundos()).isEqualTo(60);

        // 2º estouro dobraria para 120s, mas o teto é 90s.
        relogio.avancar(Duration.ofSeconds(61));
        assertThat(limitado.tentar("ip-4", 1, 60).permitido()).isTrue();
        assertThat(limitado.tentar("ip-4", 1, 60).retryAfterSegundos()).isEqualTo(90);
    }

    @Test
    void chavesDiferentesSaoIndependentes() {
        assertThat(limiter.tentar("a", 1, 60).permitido()).isTrue();
        assertThat(limiter.tentar("a", 1, 60).permitido()).isFalse();

        assertThat(limiter.tentar("b", 1, 60).permitido()).isTrue();
    }

    @Test
    void restanteDecrementaDentroDaJanela() {
        assertThat(limiter.tentar("ip-5", 3, 60).restante()).isEqualTo(2);
        assertThat(limiter.tentar("ip-5", 3, 60).restante()).isEqualTo(1);
        assertThat(limiter.tentar("ip-5", 3, 60).restante()).isZero();
    }

    @Test
    void janelaExpiraEVoltaAContarSemBloquear() {
        assertThat(limiter.tentar("ip-6", 2, 60).permitido()).isTrue();
        assertThat(limiter.tentar("ip-6", 2, 60).permitido()).isTrue();

        relogio.avancar(Duration.ofSeconds(60));

        assertThat(limiter.tentar("ip-6", 2, 60).permitido()).isTrue();
    }
}
