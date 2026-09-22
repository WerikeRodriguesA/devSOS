package com.devsos.infrastructure.web.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes do filtro: mapeamento de rota, 429 + Retry-After e headers. */
class RateLimitFilterTest {

    private final RelogioFake relogio = new RelogioFake(Instant.parse("2026-09-21T12:00:00Z"));

    private RateLimitProperties props(int limiteLogin, int limiteFeed, boolean enabled) {
        return new RateLimitProperties(
            enabled,
            new RateLimitProperties.Regra(limiteLogin, 60),
            new RateLimitProperties.Regra(5, 60),
            new RateLimitProperties.Regra(10, 60),
            new RateLimitProperties.Regra(limiteFeed, 60),
            300,
            false
        );
    }

    private RateLimitFilter filtro(RateLimitProperties props) {
        return new RateLimitFilter(props, new RateLimiter(relogio, props.maxCooldownSegundos()),
            JsonMapper.builder().build());
    }

    private MockHttpServletRequest request(String metodo, String uri, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, uri);
        req.setRequestURI(uri);
        req.setRemoteAddr(ip);
        return req;
    }

    private record Resposta(MockHttpServletResponse response, MockFilterChain chain) {
        boolean passou() {
            return chain.getRequest() != null;
        }
    }

    private Resposta executar(RateLimitFilter filtro, MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filtro.doFilter(req, res, chain);
        return new Resposta(res, chain);
    }

    @Test
    void bloqueiaLoginAcimaDoLimiteCom429ERetryAfter() throws Exception {
        RateLimitFilter filtro = filtro(props(2, 60, true));

        assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.1")).passou()).isTrue();
        assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.1")).passou()).isTrue();

        Resposta terceira = executar(filtro, request("POST", "/api/auth/login", "10.0.0.1"));
        assertThat(terceira.passou()).isFalse();
        assertThat(terceira.response().getStatus()).isEqualTo(429);
        assertThat(terceira.response().getHeader("Retry-After")).isEqualTo("60");
        assertThat(terceira.response().getHeader("X-RateLimit-Limit")).isEqualTo("2");
        assertThat(terceira.response().getContentAsString()).contains("\"status\":429");
    }

    @Test
    void ipDiferenteNaoEhAfetadoPeloBloqueioDeOutro() throws Exception {
        RateLimitFilter filtro = filtro(props(1, 60, true));

        assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.1")).passou()).isTrue();
        assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.1")).passou()).isFalse();

        assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.2")).passou()).isTrue();
    }

    @Test
    void rotaNaoMapeadaNaoEhLimitada() throws Exception {
        RateLimitFilter filtro = filtro(props(1, 60, true));

        for (int i = 0; i < 5; i++) {
            assertThat(executar(filtro, request("POST", "/api/posts", "10.0.0.1")).passou()).isTrue();
        }
    }

    @Test
    void feedTemLimiteProprio() throws Exception {
        RateLimitFilter filtro = filtro(props(5, 2, true));

        assertThat(executar(filtro, request("GET", "/api/posts", "10.0.0.1")).passou()).isTrue();
        assertThat(executar(filtro, request("GET", "/api/posts", "10.0.0.1")).passou()).isTrue();

        Resposta terceira = executar(filtro, request("GET", "/api/posts", "10.0.0.1"));
        assertThat(terceira.response().getStatus()).isEqualTo(429);
    }

    @Test
    void registerErefreshTambemSaoLimitados() throws Exception {
        RateLimitFilter filtro = filtro(props(5, 60, true));

        assertThat(executar(filtro, request("POST", "/api/auth/register", "10.0.0.9")).passou()).isTrue();
        assertThat(executar(filtro, request("POST", "/api/auth/register", "10.0.0.9")).passou()).isTrue();
        assertThat(executar(filtro, request("POST", "/api/auth/register", "10.0.0.9")).passou()).isTrue();
        assertThat(executar(filtro, request("POST", "/api/auth/register", "10.0.0.9")).passou()).isTrue();
        assertThat(executar(filtro, request("POST", "/api/auth/register", "10.0.0.9")).passou()).isTrue();
        assertThat(executar(filtro, request("POST", "/api/auth/register", "10.0.0.9")).response().getStatus())
            .isEqualTo(429);

        for (int i = 0; i < 10; i++) {
            assertThat(executar(filtro, request("POST", "/api/auth/refresh", "10.0.0.9")).passou()).isTrue();
        }
        assertThat(executar(filtro, request("POST", "/api/auth/refresh", "10.0.0.9")).response().getStatus())
            .isEqualTo(429);
    }

    @Test
    void retryAfterCresceNoCooldownProgressivo() throws Exception {
        RateLimitFilter filtro = filtro(props(1, 60, true));

        assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.7")).passou()).isTrue();
        assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.7")).response().getHeader("Retry-After"))
            .isEqualTo("60");

        relogio.avancar(Duration.ofSeconds(61));

        assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.7")).passou()).isTrue();
        assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.7")).response().getHeader("Retry-After"))
            .isEqualTo("120");
    }

    @Test
    void desligadoNaoLimita() throws Exception {
        RateLimitFilter filtro = filtro(props(1, 1, false));

        for (int i = 0; i < 10; i++) {
            assertThat(executar(filtro, request("POST", "/api/auth/login", "10.0.0.1")).passou()).isTrue();
        }
    }
}
