package com.devsos.application.service;

import com.devsos.application.dto.auth.AuthResponseDTO;
import com.devsos.application.dto.auth.LoginRequestDTO;
import com.devsos.application.dto.auth.RegistroRequestDTO;
import com.devsos.application.dto.user.UserProfileResponseDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.UserRepository;
import com.devsos.infrastructure.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço de autenticação: registro (criação de conta) e login (emissão de JWT).
 *
 * <h2>E o Spring Security?</h2>
 * O "login" aqui NÃO usa o filtro de formulário do Spring Security. Nós mesmos
 * validamos e-mail/senha (com o {@link PasswordEncoder}), e o Spring Security
 * entra na jogada apenas como guarda da rota (o {@code JwtAuthenticationFilter}).
 * É a abordagem mais comum em APIs REST com JWT.
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
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    @Transactional(readOnly = true)
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
        String token = jwtService.gerarToken(usuario.getId(), usuario.getEmail(), usuario.getNome());
        return new AuthResponseDTO(token, jwtService.getExpiracaoSegundos(), UserProfileResponseDTO.from(usuario));
    }
}