package com.devsos.application.service;

import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.application.exception.ResourceNotFoundException;
import com.devsos.domain.passwordreset.PasswordResetTokenEntity;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.notification.RecuperacaoSenhaNotifier;
import com.devsos.infrastructure.repository.PasswordResetTokenRepository;
import com.devsos.infrastructure.repository.RefreshTokenRepository;
import com.devsos.infrastructure.repository.UserRepository;
import com.devsos.infrastructure.security.TokenOpaco;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Fluxos de senha: <b>esqueci minha senha</b> (token por e-mail),
 * <b>redefinir</b> com o token e <b>trocar</b> autenticado.
 *
 * <h2>Por que um token separado do refresh token?</h2>
 * <p>Semânticas diferentes: o refresh renova o access JWT; o token de
 * recuperação prova posse do e-mail e é de <b>uso único</b> com TTL curto.
 * Misturar os dois abriria brecha (um refresh roubado viraria reset de senha).</p>
 *
 * <h2>Anti-enumeração de e-mails</h2>
 * <p>{@link #solicitarRecuperacao} responde igual exista ou não a conta — não
 * confirmamos para um estranho se um e-mail está cadastrado.</p>
 *
 * <h2>Sessões antigas caem</h2>
 * <p>Redefinir ou trocar a senha <b>revoga todos os refresh tokens</b> do
 * usuário: quem tinha uma sessão aberta com a senha antiga é desconectado.</p>
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecuperacaoSenhaNotifier notifier;
    private final long expiracaoSegundos;
    private final boolean exporToken;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository resetTokenRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                RecuperacaoSenhaNotifier notifier,
                                @Value("${devsos.auth.reset-expiracao-segundos:1800}") long expiracaoSegundos,
                                @Value("${devsos.auth.expor-token-reset:false}") boolean exporToken) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.expiracaoSegundos = expiracaoSegundos;
        this.exporToken = exporToken;
    }

    /**
     * Gera (se a conta existir) um token de recuperação e "envia" por e-mail.
     *
     * @return o token cru apenas quando {@code devsos.auth.expor-token-reset=true}
     *         (DEV/TESTE); caso contrário {@code null}.
     */
    @Transactional
    public String solicitarRecuperacao(String email) {
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        Optional<UserEntity> encontrado = userRepository.findByEmail(normalizado);
        if (encontrado.isEmpty()) {
            // Resposta neutra: não revela que o e-mail não existe.
            return null;
        }

        UserEntity usuario = encontrado.get();
        String tokenCru = TokenOpaco.gerar();
        resetTokenRepository.save(
            new PasswordResetTokenEntity(usuario.getId(), TokenOpaco.hash(tokenCru), expiracaoSegundos));
        limparExpirados();
        notifier.enviar(usuario.getEmail(), tokenCru);
        return exporToken ? tokenCru : null;
    }

    /** Redefine a senha usando um token válido (uso único, dentro do TTL). */
    @Transactional
    public void redefinirSenha(String tokenCru, String novaSenha) {
        PasswordResetTokenEntity token = resetTokenRepository.findByTokenHash(TokenOpaco.hash(tokenCru))
            .orElseThrow(() -> new RegraDeNegocioException("Token de recuperação inválido ou expirado."));

        if (token.isUsado() || token.estaExpirado()) {
            throw new RegraDeNegocioException("Token de recuperação inválido ou expirado.");
        }

        UserEntity usuario = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new RegraDeNegocioException("Token de recuperação inválido ou expirado."));

        usuario.setPasswordHash(passwordEncoder.encode(novaSenha));
        userRepository.save(usuario);

        token.usar();
        resetTokenRepository.save(token);

        // Segurança: sessões abertas com a senha antiga deixam de valer.
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), Instant.now());
        limparExpirados();
    }

    /** Troca de senha autenticada: exige a senha atual correta. */
    @Transactional
    public void trocarSenha(UUID userId, String senhaAtual, String novaSenha) {
        UserEntity usuario = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (!passwordEncoder.matches(senhaAtual, usuario.getPasswordHash())) {
            throw new RegraDeNegocioException("Senha atual incorreta.");
        }
        if (passwordEncoder.matches(novaSenha, usuario.getPasswordHash())) {
            throw new RegraDeNegocioException("A nova senha deve ser diferente da atual.");
        }

        usuario.setPasswordHash(passwordEncoder.encode(novaSenha));
        userRepository.save(usuario);

        // Segurança: derruba as sessões antigas após a troca.
        refreshTokenRepository.revogarTodosDoUsuario(userId, Instant.now());
    }

    private void limparExpirados() {
        resetTokenRepository.deleteByExpiresAtBefore(Instant.now().minusSeconds(1));
    }
}
