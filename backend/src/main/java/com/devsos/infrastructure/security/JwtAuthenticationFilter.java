package com.devsos.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro JWT — a "recepção" do sistema.
 *
 * <p>Todo filtro do Spring obrigatoriamente: inspeciona a requisição →
 * opcionalmente escreve algo no {@link SecurityContextHolder} → chama
 * {@code filterChain.doFilter()} para a requisição seguir o caminho normal.</p>
 *
 * <h2>Fluxo da autenticação JWT passo a passo</h2>
 * <ol>
 *   <li>Se a URL já é pública (ex.: {@code POST /api/auth/register}), este
 *       filtro nem roda — o Spring Security ignora antes.</li>
 *   <li>Lemos o cabeçalho {@code Authorization: Bearer <token>}.</li>
 *   <li>{@link JwtService#validarToken(String)} confere a assinatura e a
 *       expiração → devolve o {@link IdUsuarioLogado}.</li>
 *   <li>Criamos um {@code UsernamePasswordAuthenticationToken} com a
 *       identidade e marcamos como "autenticado".</li>
 *   <li>Guardamos no {@link SecurityContextHolder}: é daqui que os controllers
 *       leem "quem é o usuário logado" (sem consultar o banco).</li>
 * </ol>
 *
 * <p>Se o token estiver ausente OU inválido, simplesmente NÃO setamos a
 * autenticação — a requisição segue e, se a rota exigir login, o Spring
 * devolve 401 (mensagem tratada em {@code RestAuthenticationEntryPoint}).</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO_BEARER = "Bearer ";
    private static final String ROLE_USUARIO = "ROLE_USUARIO";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIXO_BEARER) && estahAutenticado() == false) {
            IdUsuarioLogado principal = jwtService.validarToken(header.substring(PREFIXO_BEARER.length()));

            var authentication = new UsernamePasswordAuthenticationToken(
                principal,                       // o "quem"
                null,                            // credencial (sem senha aqui)
                List.of(new SimpleGrantedAuthority(ROLE_USUARIO)) // permissões
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean estahAutenticado() {
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }
}