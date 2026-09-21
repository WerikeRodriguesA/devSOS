package com.devsos.infrastructure.web.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;

/**
 * Registro do {@link RateLimitFilter}.
 *
 * <p>Registrar via {@link FilterRegistrationBean} (em vez de {@code @Component})
 * deixa a configuração determinística: URL patterns só nas rotas que nos
 * interessam e ordem {@link Ordered#HIGHEST_PRECEDENCE} + 10 — logo depois do
 * {@code MaxRequestBodySizeFilter} e ANTES da cadeia de segurança. Assim o
 * abuso é cortado na porta, sem custo de JWT/BCrypt.</p>
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitWebConfig {

    @Bean
    public RateLimiter rateLimiter(RateLimitProperties props) {
        return new RateLimiter(Clock.systemUTC(), props.maxCooldownSegundos());
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties props, RateLimiter limiter, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registro =
            new FilterRegistrationBean<>(new RateLimitFilter(props, limiter, objectMapper));
        registro.setUrlPatterns(List.of("/api/auth/*", "/api/posts"));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registro;
    }
}
