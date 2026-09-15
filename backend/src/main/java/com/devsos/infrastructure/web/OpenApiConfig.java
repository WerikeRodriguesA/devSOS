package com.devsos.infrastructure.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do OpenAPI/Swagger UI (springdoc).
 *
 * <p>A dependência {@code springdoc-openapi-starter-webmvc-ui} já entrega,
 * sem configurar nada: a spec OpenAPI 3 em {@code /v3/api-docs} (e
 * {@code /v3/api-docs.yaml}) e a UI interativa em {@code /swagger-ui} — onde
 * dá para testar cada endpoint pelo botão "Try it out".</p>
 *
 * <p>O que este bean adiciona:
 * <ul>
 *   <li><b>Info</b>: título/descrição da API (metadados que aparecem no topo
 *       da UI);</li>
 *   <li><b>Esquema de segurança {@code bearerAuth}</b>: declara que a API
 *       autentica com JWT no cabeçalho {@code Authorization: Bearer ...}.
 *       Sem isso a UI nem tem o botão "Authorize" — e o "Try it out" nos
 *       endpoints protegidos sempre responderia 401.</li>
 *   <li><b>SecurityRequirement global</b>: por padrão TODO endpoint exige
 *       JWT (igual à regra do {@code SecurityConfig}). Os endpoints realmente
 *       públicos (register/login/refresh, GETs do feed/perfil) removem essa
 *       exigência com {@code @SecurityRequirements(value = {})} direto no
 *       controller — assim o "cadeado" da UI bate com o comportamento real.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI devsosOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("devSOS API")
                .version("1.0.0")
                .description("""
                    Perfis de usuários, feed de posts, salas de corrida com chat
                    em tempo real e autenticação por JWT (access + refresh token).

                    Use o botão **Authorize** para colar o access token JWT e
                    testar os endpoints protegidos.
                    """))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}