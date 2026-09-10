package com.devsos.infrastructure.security;

import com.devsos.application.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Respostas 401 / 403 NO MESMO FORMATO do resto da API ({@link ApiError}).
 *
 * <p>Sem esta classe, o Spring Security devolveria a página padrão de erro
 * (XML/HTML), quebrando o contrato único de erros que definimos no
 * {@code GlobalExceptionHandler}.</p>
 *
 * <h2>401 vs 403 — a diferença que importa</h2>
 * <ul>
 *   <li><b>401 Unauthorized</b> = "cadê o crachá?" — não veio token (ou inválido).</li>
 *   <li><b>403 Forbidden</b> = "crachá válido, mas você não pode entrar aqui" —
 *       veio token, porém sem a permissão necessária.</li>
 * </ul>
 *
 * <p>O erro é escrito manualmente no {@code HttpServletResponse} via
 * {@link ObjectMapper} — exatamente o mesmo JSON do {@code ApiError}.</p>
 */
@Component
public class RestAuthenticationHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /**
     * {@link ObjectMapper} próprio — não injetado do Spring porque no Spring
     * Boot 4 o {@code ObjectMapper} beans padrão (com registro automático)
     * nem sempre é encontrado para injeção. Para um erro HTTP basta um mapper
     * puro sem customizações do container.
     * <p>O {@code JavaTimeModule} é necessário para serializar {@link Instant}
     * (campo {@code timestamp} do {@code ApiError}) como ISO-8601 em vez de
     * número de nanossegundos.</p>
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void commence(jakarta.servlet.http.HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        escreverErro(response, 401, "Não autenticado. Envie o token no cabeçalho Authorization.");
    }

    @Override
    public void handle(jakarta.servlet.http.HttpServletRequest request,
                       HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException {
        escreverErro(response, 403, "Você não tem permissão para realizar esta ação.");
    }

    private void escreverErro(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        var reason = status == 401 ? "Unauthorized" : "Forbidden";
        objectMapper.writeValue(response.getWriter(), ApiError.of(status, reason, mensagem));
    }
}