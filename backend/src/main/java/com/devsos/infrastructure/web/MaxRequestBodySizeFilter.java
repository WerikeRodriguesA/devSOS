package com.devsos.infrastructure.web;

import com.devsos.application.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Limite de tamanho do corpo ({@code Content-Length} real) em {@code POST /api/posts}.
 *
 * <h2>Por que este filtro existe? (Roadmap)</h2>
 * As validações de campo ({@code @Size}) limitam o <em>conteúdo semântico</em>
 * (título ≤ 160 chars etc.), mas não o <em>tamanho em bytes</em> do JSON que
 * chega. Sem uma trava no corpo, um cliente malicioso pode mandar um payload
 * gigante e forçar o servidor a desserializar/guardar à toa. Este filtro
 * barra na porta: corpo acima de {@code maxBodyBytes} (default 64 KiB) é
 * recusado com {@code 413 Payload Too Large} no envelope padrão {@link ApiError}.
 *
 * <h2>Ordem de execução</h2>
 * Registrado via {@code FilterRegistrationBean} com
 * {@link Ordered#HIGHEST_PRECEDENCE} (no {@code MaxBodySizeWebConfig}) para
 * rodar ANTES da cadeia de segurança: um payload gigante é recusado com 413
 * sem nem passar pelo JWT — custo mínimo.
 *
 * <h2>Cobre pedaços (chunked) e Content-Length mentiroso</h2>
 * Não confiamos só no cabeçalho: lemos até {@code maxBodyBytes + 1} bytes do
 * corpo real. Se houver mais que o limite, 413; se o corpo for menor, embrulhamos
 * a request num buffer reutilizável e seguimos a cadeia normalmente (o
 * {@code @RequestBody} do Controller continua lendo o JSON do mesmo jeito).
 */
public class MaxRequestBodySizeFilter extends OncePerRequestFilter {

    static final String ROTA = "/api/posts";
    private static final String METODO = "POST";

    private final int maxBodyBytes;
    private final ObjectMapper objectMapper;

    public MaxRequestBodySizeFilter(
            @Value("${devsos.posts.max-body-bytes:65536}") int maxBodyBytes,
            ObjectMapper objectMapper) {
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("devsos.posts.max-body-bytes deve ser > 0, veio: " + maxBodyBytes);
        }
        this.maxBodyBytes = maxBodyBytes;
        // ObjectMapper GERENCIADO pelo Spring (Jackson 3): já vem com o suporte
        // a java.time (Instant) — criar um `new ObjectMapper()` aqui daria
        // InvalidDefinitionException ao serializar o timestamp do ApiError.
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Guarda só a criação de post; GET /api/posts e outras rotas passam reto.
        if (!METODO.equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !ROTA.equals(path) && !(ROTA + "/").equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Ataque de "varinha mágica": Content-Length declarado acima do limite.
        if (request.getContentLengthLong() > maxBodyBytes) {
            rejeitar(response);
            return;
        }

        // Precisamos ler o corpo para medir de verdade (cobre chunked e
        // Content-Length mentiroso). Lê no MÁXIMO maxBodyBytes+1 — se sobrar
        // byte, é porque passou do limite.
        int limiteLeitura = maxBodyBytes + 1;
        byte[] corpo = request.getInputStream().readNBytes(limiteLeitura);

        if (corpo.length > maxBodyBytes) {
            rejeitar(response);
            return;
        }

        // Corpo dentro do limite: entrega uma request com o corpo reutilizável,
        // senão o InputStream consumido faria o Controller ler corpo vazio.
        filterChain.doFilter(new BodyBufferedReader(request, corpo), response);
    }

    private void rejeitar(HttpServletResponse response) throws IOException {
        ApiError erro = ApiError.of(
            HttpStatus.PAYLOAD_TOO_LARGE.value(),
            HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase(),
            "Corpo da requisição excede o limite de " + maxBodyBytes + " bytes para POST /api/posts."
        );
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(erro));
    }

    /**
     * Wrapper que devolve o corpo já lido como novo {@link ServletInputStream},
     * preservando o {@code Content-Length} correto para o Spring/Jackson.
     */
    static final class BodyBufferedReader extends HttpServletRequestWrapper {

        private final byte[] corpo;

        BodyBufferedReader(HttpServletRequest request, byte[] corpo) {
            super(request);
            this.corpo = corpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream fonte = new ByteArrayInputStream(corpo);
            return new ServletInputStream() {
                @Override public int read() { return fonte.read(); }
                @Override public boolean isFinished() { return fonte.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(jakarta.servlet.ReadListener listener) {
                    throw new UnsupportedOperationException("Streaming não suportado neste wrapper.");
                }
            };
        }

        @Override
        public int getContentLength() {
            return corpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return corpo.length;
        }
    }
}