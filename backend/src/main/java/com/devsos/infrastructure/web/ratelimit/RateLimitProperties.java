package com.devsos.infrastructure.web.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do rate limiting (proteção anti brute-force em rotas públicas).
 *
 * <p>Rotas públicas sensíveis ({@code POST /api/auth/login},
 * {@code /register}, {@code /refresh}) e o feed ({@code GET /api/posts}) ganham
 * um teto de requisições por IP numa janela de tempo. Estourou? {@code 429}
 * com {@link ApiError} e cabeçalho {@code Retry-After} — e o bloqueio cresce a
 * cada novo estouro (cooldown progressivo).</p>
 *
 * <p>Propriedades (variáveis de ambiente {@code DEV_SOS_RATE_LIMIT_*}):</p>
 * <pre>{@code
 * devsos.rate-limit.enabled=true
 * devsos.rate-limit.login.limite=5          # tentativas por janela
 * devsos.rate-limit.login.janela-segundos=60
 * devsos.rate-limit.register.limite=5
 * devsos.rate-limit.register.janela-segundos=60
 * devsos.rate-limit.refresh.limite=10
 * devsos.rate-limit.refresh.janela-segundos=60
 * devsos.rate-limit.feed.limite=60          # feed é leitura: teto bem mais folgado
 * devsos.rate-limit.feed.janela-segundos=60
 * devsos.rate-limit.max-cooldown-segundos=300
 * devsos.rate-limit.trust-forwarded-header=false  # true só atrás de proxy confiável
 * }</pre>
 */
@ConfigurationProperties(prefix = "devsos.rate-limit")
public record RateLimitProperties(
    Boolean enabled,
    Regra login,
    Regra register,
    Regra refresh,
    Regra feed,
    Integer maxCooldownSegundos,
    Boolean trustForwardedHeader
) {

    /** Teto de {@code limite} requisições por {@code janelaSegundos} segundos. */
    public record Regra(Integer limite, Integer janelaSegundos) {
        public Regra {
            if (limite == null || limite <= 0) limite = 5;
            if (janelaSegundos == null || janelaSegundos <= 0) janelaSegundos = 60;
        }
    }

    public RateLimitProperties {
        if (enabled == null) enabled = true;
        if (maxCooldownSegundos == null || maxCooldownSegundos <= 0) maxCooldownSegundos = 300;
        if (trustForwardedHeader == null) trustForwardedHeader = false;
        if (login == null) login = new Regra(5, 60);
        if (register == null) register = new Regra(5, 60);
        if (refresh == null) refresh = new Regra(10, 60);
        if (feed == null) feed = new Regra(60, 60);
    }

    public boolean estaHabilitado() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * Confiar no {@code X-Forwarded-For}? Deixe {@code false} (padrão) a menos
     * que a app esteja atrás de um proxy reverso CONFIÁVEL: o cabeçalho é
     * forjável pelo cliente e, se usado sem proxy, permitiria burlar o limite
     * trocando o valor a cada requisição.
     */
    public boolean confiarEmProxy() {
        return Boolean.TRUE.equals(trustForwardedHeader);
    }
}
