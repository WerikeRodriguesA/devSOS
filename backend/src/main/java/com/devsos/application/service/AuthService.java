package com.devsos.application.service;

import com.devsos.application.dto.auth.AuthResponseDTO;
import com.devsos.application.dto.auth.LoginRequestDTO;
import com.devsos.application.dto.auth.RegistroRequestDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço de autenticação: registro (criação de conta) e login (emissão de
 * credenciais).
 *
 * <h2>E o Spring Security?</h2>
 * O "login" aqui NÃO usa o filtro de formulário do Spring Security. Nós mesmos
 * validamos e-mail/senha (com o {@link PasswordEncoder}), e o Spring Security
 * entra na jogada apenas como guarda da rota (o {@code JwtAuthenticationFilter}).
 * É a abordagem mais comum em APIs REST com JWT.
 *
 * <h2>O par de tokens</h2>
 * Cada login/cadastro devolve <b>access JWT</b> (curto) + <b>refresh token</b>
 * (opaco, TTL longo). Quem emite/rotaciona/revoga o lado do refresh é o
 * {@link RefreshTokenService} — a revogação (logout / logout forçado) é o que
 * permite "deslogar" um fluxo que usa JWT.
 *
 * <h2>Duas regras de negócio importantes</h2>
 * <ul>
 *   <li><b>E-mail único:</b> {@code existsByEmail} impede contas duplicadas
 *       (o banco também garante com {@code UNIQUE (email)}).</li>
 *   <li><b>Senha nunca em texto puro:</b> guardamos apenas o HASH BCrypt
 *       ({@link PasswordEncoder#encode}). Nem o DTO nem o perfil expõem a senha.</li>
 * </ul>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponseDTO registrar(RegistroRequestDTO request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new RegraDeNegocioException("Já existe uma conta com este e-mail.");
        }

        String github = request.githubUsername() == null ? "" : request.githubUsername().trim();
        String nome = request.nome().trim();

        UserEntity novoUsuario = new UserEntity(
            nome,
            email,
            github,
            passwordEncoder.encode(request.senha())
        );

        UserEntity salvo = userRepository.saveAndFlush(novoUsuario);
        return montarResposta(salvo);
    }

    /**
     * Fluxo: valida e-mail/senha e emite o par de tokens (access JWT +
     * refresh). <b>Não é read-only:</b> emitir o refresh token GRAVA uma linha
     * em {@code refresh_tokens} — uma transação read-only descartaria o INSERT
     * (o token sairia da resposta mas nunca seria persistido).
     */
    @Transactional
    public AuthResponseDTO login(LoginRequestDTO request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);

        UserEntity usuario = userRepository.findByEmail(email)
            .orElseThrow(() -> new RegraDeNegocioException("E-mail ou senha inválidos."));

        if (!passwordEncoder.matches(request.senha(), usuario.getPasswordHash())) {
            throw new RegraDeNegocioException("E-mail ou senha inválidos.");
        }

        return montarResposta(usuario);
    }

    private AuthResponseDTO montarResposta(UserEntity usuario) {
        return refreshTokenService.emitirTokens(usuario);
    }
}