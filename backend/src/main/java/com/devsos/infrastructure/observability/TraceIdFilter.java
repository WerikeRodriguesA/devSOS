package com.devsos.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro de rastreio (issue #18): garante que toda requisicao tenha um
 * traceId no MDC para correlacionar os logs, e responda com X-Trace-Id.
 *
 * <p>Fases:</p>
 * <ul>
 *   <li>Se o cliente mandou um header {@code X-Trace-Id}, respeitamos
 *       (composicao distribuida), sem passar de 100 chars.</li>
 *   <li>Se nao veio, geramos um UUID v4 curto (sem hifens).</li>
 *   <li>Colocamos no MDC como {@code traceId} - os appenders JSON usam
 *       esse campo em todo log da requisicao.</li>
 *   <li>Devolvemos o mesmo valor no header de resposta
 *       {@code X-Trace-Id} (o cliente usa para o suporte localizar a
 *       corrida).</li>
 *   <li>No fim, removemos do MDC - senao o traceId "vaza" para a proxima
 *       requisicao na mesma thread.</li>
 * </ul>
 *
 * <p>Registrado com {@code @Order(Ordered.HIGHEST_PRECEDENCE)}: roda antes
 * da cadeia de filtros do Spring Security, entao o MDC ja esta populado
 * mesmo para logs de autenticacao (401/403, JWT invalido).</p>
 *
 * <p>Sem dependencia nova (100% offline): apenas Jakarta Servlet + SLF4J
 * MDC, que ja fazem parte do app. O follow-up (issue #18, docs/API.md)
 * troca por tracing-bridge-brave quando a rede voltar, mantendo o mesmo
 * header X-Trace-Id.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    static final String MDC_TRACE_ID = "traceId";
    static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final int MAX_TRACE_ID = 100;

    private final MetricasDeSOS metricas;

    public TraceIdFilter(MetricasDeSOS metricas) {
        this.metricas = metricas;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = request.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        } else if (traceId.length() > MAX_TRACE_ID) {
            traceId = traceId.substring(0, MAX_TRACE_ID);
        }

        try {
            MDC.put(MDC_TRACE_ID, traceId);
            response.setHeader(HEADER_TRACE_ID, traceId);

            filterChain.doFilter(request, response);

            int status = response.getStatus();
            if (status >= 400 && status < 500) {
                metricas.registrarHttp4xx();
            } else if (status >= 500) {
                metricas.registrarHttp5xx();
            }
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }
}