package com.devsos.infrastructure.web.ratelimit;

import com.devsos.application.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Filtro de rate limiting para rotas públicas sensíveis.
 *
 * <h2>O que ele protege e por quê</h2>
 * <p>{@code POST /api/auth/login} e {@code /register} são públicos e sem
 * proteção: sem teto, viram alvo fácil de brute force de senha e de criação em
 * massa de contas. O filtro também aplica um teto bem mais folgado ao feed
 * ({@code GET /api/posts}), que é leitura pública.</p>
 *
 * <h2>Resposta ao estouro</h2>
 * <p>{@code 429 Too Many Requests} no envelope padrão {@link ApiError}, mais os
 * cabeçalhos {@code Retry-After} (segundos) e {@code X-RateLimit-Limit}/
 * {@code X-RateLimit-Remaining}. A mensagem é <b>neutra</b> — não revela se um
 * usuário/e-mail específico foi bloqueado (isso vazaria informação).</p>
 *
 * <h2>Chave do limite</h2>
 * <p>Rota + IP do cliente. Por padrão usamos {@code getRemoteAddr()}; o
 * {@code X-Forwarded-For} só é considerado quando
 * {@code devsos.rate-limit.trust-forwarded-header=true} (proxy confiável),
 * pois é um cabeçalho forjável.</p>
 *
 * <h2>Ordem</h2>
 * <p>Registrado com precedência altíssima (via {@code RateLimitWebConfig}),
 * ANTES da cadeia de segurança: barra o abuso sem gastar JWT/BCrypt.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitProperties props, RateLimiter limiter, ObjectMapper objectMapper) {
        this.props = props;
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !props.estaHabilitado() || regraDe(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        RegraAplicavel regra = regraDe(request);
        if (regra == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String chave = regra.nome() + ":" + ipDoCliente(request);
        RateLimiter.Decisao decisao = limiter.tentar(chave, regra.limite(), regra.janelaSegundos());

        response.setHeader("X-RateLimit-Limit", Integer.toString(regra.limite()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decisao.restante()));

        if (!decisao.permitido()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decisao.retryAfterSegundos()));
            rejeitar(response, decisao.retryAfterSegundos());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Qual regra vale para a requisição? {@code null} = rota não limitada. */
    private RegraAplicavel regraDe(HttpServletRequest request) {
        String metodo = request.getMethod();
        String path = request.getRequestURI();

        if ("POST".equalsIgnoreCase(metodo)) {
            if ("/api/auth/login".equals(path)) return para("login", props.login());
            if ("/api/auth/register".equals(path)) return para("register", props.register());
            if ("/api/auth/refresh".equals(path)) return para("refresh", props.refresh());
        } else if ("GET".equalsIgnoreCase(metodo)) {
            if ("/api/posts".equals(path) || "/api/posts/".equals(path)) return para("feed", props.feed());
        }
        return null;
    }

    private static RegraAplicavel para(String nome, RateLimitProperties.Regra regra) {
        return new RegraAplicavel(nome, regra.limite(), regra.janelaSegundos());
    }

    private String ipDoCliente(HttpServletRequest request) {
        if (props.confiarEmProxy()) {
            String encaminhado = request.getHeader("X-Forwarded-For");
            if (encaminhado != null && !encaminhado.isBlank()) {
                int virgula = encaminhado.indexOf(',');
                return (virgula > 0 ? encaminhado.substring(0, virgula) : encaminhado).trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void rejeitar(HttpServletResponse response, long retryAfterSegundos) throws IOException {
        ApiError erro = ApiError.of(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
            "Muitas tentativas em pouco tempo. Aguarde " + retryAfterSegundos
                + "s e tente novamente."
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(erro));
    }

    private record RegraAplicavel(String nome, int limite, int janelaSegundos) {
    }
}
