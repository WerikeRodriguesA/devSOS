package com.devsos.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Regras de segurança da aplicação.
 *
 * <h2>Quais rotas são públicas e quais exigem login?</h2>
 * <ul>
 *   <li><b>Públicas (abertas):</b> {@code POST /api/auth/register},
 *       {@code POST /api/auth/login} e todos os {@code GET} (o feed é público
 *       como o Instagram: qualquer um navega sem logar).</li>
 *   <li><b>Protegidas (exigem JWT):</b> criar post ({@code POST /api/posts}),
 *       atualizar perfil ({@code PATCH /api/users/...}), e todo o resto.</li>
 * </ul>
 *
 * <h2>Por que {@code SessionCreationPolicy.STATELESS}?</h2>
 * O Spring Security por padrão cria {@code JSESSIONID} (cookie de sessão no
 * servidor). Para uma API REST consumida por apps/mobile/webSPA, o padrão é
 * NÃO guardar estado no servidor: cada requisição se autentica pelo token JWT
 * no cabeçalho. (É o que permite escalar várias instâncias sem sessões
 * compartilhadas.)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationHandler restAuthenticationHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationHandler restAuthenticationHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationHandler = restAuthenticationHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // REST sem cookies → CSRF não se aplica
            // CORS: leva em conta o CorsConfigurationSource do CorsConfig
            // (dev-gated por devsos.cors.allowed-origins). Sem origens
            // configuradas, nenhum acesso cross-origin é liberado.
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(restAuthenticationHandler)  // 401
                .accessDeniedHandler(restAuthenticationHandler))      // 403
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/**").permitAll()
                // Handshake do WebSocket: o JWT é validado pelo JwtWebSocketHandshakeInterceptor
                // (o filtro HTTP do REST não alcança o trânsito de mensagens posterior).
                .requestMatchers("/ws-devsos/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Codificador de senhas usado no registro e no login.
     * BCrypt é a escolha padrão do mercado: hash LENTO proposital (dificulta
     * brute-force) e com "salt" automático por usuário.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}