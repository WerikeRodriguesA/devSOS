package com.devsos.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Registro EXPLÍCITO do {@link MaxRequestBodySizeFilter}.
 *
 * <p>Registrar via {@link FilterRegistrationBean} (em vez de {@code @Component})
 * deixa a configuração determinística: URL pattern só em {@code /api/posts} e
 * ordem {@link Ordered#HIGHEST_PRECEDENCE} — garantindo que o 413 por corpo
 * gigante aconteça ANTES da cadeia de segurança (JWT), bloqueando o payload na
 * porta com custo mínimo.</p>
 */
@Configuration
public class MaxBodySizeWebConfig {

    private final int maxBodyBytes;
    private final ObjectMapper objectMapper;

    public MaxBodySizeWebConfig(
            @Value("${devsos.posts.max-body-bytes:65536}") int maxBodyBytes,
            ObjectMapper objectMapper) {
        this.maxBodyBytes = maxBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Bean
    public FilterRegistrationBean<MaxRequestBodySizeFilter> maxBodySizeFilterRegistration() {
        FilterRegistrationBean<MaxRequestBodySizeFilter> registro =
            new FilterRegistrationBean<>(new MaxRequestBodySizeFilter(maxBodyBytes, objectMapper));
        registro.setUrlPatterns(List.of("/api/posts"));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}