package com.devsos.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS para desenvolvimento (dev-gated).
 *
 * <p>O app mobile não precisa disto (não roda em navegador), mas o <b>Dev
 * Client Web</b> — uma ferramenta interna que roda em outro endereço local —
 * é bloqueado pelo navegador sem os cabeçalhos {@code Access-Control-*}.
 * Este arquivo expõe o CORS apenas quando a propriedade
 * {@code devsos.cors.allowed-origins} vier preenchida (via
 * {@code DEV_SOS_CORS_ALLOWED_ORIGINS} ou argumento).</p>
 *
 * <pre>
 *   Ex.: DEV_SOS_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
 * </pre>
 *
 * <p><b>Gate de segurança:</b> vazio (padrão) liga a config com ZERO origens —
 * ou seja, nenhuma origem cross-origin recebe acesso, preservando o
 * comportamento original. Liberar origens continua sendo decisão do ambiente.</p>
 *
 * <p>O {@code CorsConfigurationSource} é consumido tanto pelo Spring MVC
 * (cabeçalhos nas respostas) quanto pelo Spring Security
 * ({@code http.cors(...)}) para responder o PREFLIGHT {@code OPTIONS} — que,
 * sem isso, caía no filtro de segurança e levava 401 antes mesmo de chegar ao
 * controller.</p>
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${devsos.cors.allowed-origins:}") String allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            // Comportamento original (dev-gated): nenhuma origem liberada.
            config.setAllowedOrigins(List.of());
        } else {
            config.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList()
            );
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}