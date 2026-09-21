package com.devsos.application.service;

import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.domain.passwordreset.PasswordResetTokenEntity;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.notification.RecuperacaoSenhaNotifier;
import com.devsos.infrastructure.repository.PasswordResetTokenRepository;
import com.devsos.infrastructure.repository.RefreshTokenRepository;
import com.devsos.infrastructure.repository.UserRepository;
import com.devsos.infrastructure.security.TokenOpaco;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    /** Hash placeholder legado da V1: não é uma senha BCrypt válida. */
    private static final String PLACEHOLDER = "$2a$10$placeholder";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository resetTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RecuperacaoSenhaNotifier notifier;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, resetTokenRepository,
            refreshTokenRepository, passwordEncoder, notifier, 1800, false);
    }

    private PasswordResetService serviceExpondoToken() {
        return new PasswordResetService(userRepository, resetTokenRepository,
            refreshTokenRepository, passwordEncoder, notifier, 1800, true);
    }

    private UserEntity usuarioComSenha(String hash) {
        UserEntity u = new UserEntity("Bruna", "bruna@dev.com", "bruna", hash);
        return u;
    }

    // ---------------------------------------------------------- forgot-password

    @Test
    void emailInexistenteNaoCriaTokenNemNotifica() {
        when(userRepository.findByEmail("x@dev.com")).thenReturn(Optional.empty());

        String token = service.solicitarRecuperacao("X@dev.com");

        assertThat(token).isNull();
        verify(resetTokenRepository, never()).save(any());
        verify(notifier, never()).enviar(any(), any());
    }

    @Test
    void emailExistenteCriaTokenENotifica() {
        UserEntity u = usuarioComSenha(passwordEncoder.encode("senha123"));
        when(userRepository.findByEmail("bruna@dev.com")).thenReturn(Optional.of(u));

        String token = serviceExpondoToken().solicitarRecuperacao("bruna@dev.com");

        assertThat(token).isNotBlank();
        verify(resetTokenRepository).save(any(PasswordResetTokenEntity.class));
        verify(notifier).enviar(eq("bruna@dev.com"), eq(token));
    }

    @Test
    void semExporTokenARespostaNaoTrazTokenMasPersiste() {
        UserEntity u = usuarioComSenha(passwordEncoder.encode("senha123"));
        when(userRepository.findByEmail("bruna@dev.com")).thenReturn(Optional.of(u));

        String token = service.solicitarRecuperacao("bruna@dev.com");

        assertThat(token).isNull();
        verify(resetTokenRepository).save(any(PasswordResetTokenEntity.class));
    }

    // --------------------------------------------------------- reset-password

    @Test
    void resetComTokenValidoTrocaSenhaEMarcaTokenComoUsado() {
        UserEntity u = usuarioComSenha(passwordEncoder.encode("senhaAntiga"));
        UUID userId = u.getId();
        PasswordResetTokenEntity token =
            new PasswordResetTokenEntity(userId, TokenOpaco.hash("token-cru"), 1800);
        when(resetTokenRepository.findByTokenHash(TokenOpaco.hash("token-cru"))).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.redefinirSenha("token-cru", "senhaNova123");

        assertThat(passwordEncoder.matches("senhaNova123", u.getPasswordHash())).isTrue();
        assertThat(token.isUsado()).isTrue();
        verify(userRepository).save(u);
        verify(resetTokenRepository).save(token);
        verify(refreshTokenRepository).revogarTodosDoUsuario(eq(userId), any());
    }

    @Test
    void resetComTokenInexistenteFalha() {
        when(resetTokenRepository.findByTokenHash(TokenOpaco.hash("nao-existe"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinirSenha("nao-existe", "senhaNova123"))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("Token de recuperação inválido ou expirado");
    }

    @Test
    void resetComTokenExpiradoFalha() {
        PasswordResetTokenEntity expirado =
            new PasswordResetTokenEntity(UUID.randomUUID(), TokenOpaco.hash("expirado"), -10);
        when(resetTokenRepository.findByTokenHash(TokenOpaco.hash("expirado"))).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.redefinirSenha("expirado", "senhaNova123"))
            .isInstanceOf(RegraDeNegocioException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetComTokenJaUsadoFalha() {
        PasswordResetTokenEntity usado =
            new PasswordResetTokenEntity(UUID.randomUUID(), TokenOpaco.hash("usado"), 1800);
        usado.usar();
        when(resetTokenRepository.findByTokenHash(TokenOpaco.hash("usado"))).thenReturn(Optional.of(usado));

        assertThatThrownBy(() -> service.redefinirSenha("usado", "senhaNova123"))
            .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void usuarioLegadoComPlaceholderVoltaALogarAposReset() {
        // Critério de aceite: "Ana Dev" (hash placeholder) volta via reset oficial.
        UserEntity ana = usuarioComSenha(PLACEHOLDER);
        UUID userId = ana.getId();
        PasswordResetTokenEntity token =
            new PasswordResetTokenEntity(userId, TokenOpaco.hash("token-ana"), 1800);
        when(resetTokenRepository.findByTokenHash(TokenOpaco.hash("token-ana"))).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(ana));

        service.redefinirSenha("token-ana", "novaSenha123");

        assertThat(passwordEncoder.matches("novaSenha123", ana.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("qualquerCoisa", PLACEHOLDER)).isFalse();
    }

    // -------------------------------------------------------- change-password

    @Test
    void trocaComSenhaAtualErradaFalha() {
        UserEntity u = usuarioComSenha(passwordEncoder.encode("senhaCerta"));
        UUID userId = u.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.trocarSenha(userId, "senhaErrada", "senhaNova123"))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("Senha atual incorreta");
        verify(userRepository, never()).save(any());
    }

    @Test
    void trocaComSenhaAtualCertaAtualizaERevogaSessoes() {
        UserEntity u = usuarioComSenha(passwordEncoder.encode("senhaCerta"));
        UUID userId = u.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        service.trocarSenha(userId, "senhaCerta", "senhaNova123");

        assertThat(passwordEncoder.matches("senhaNova123", u.getPasswordHash())).isTrue();
        verify(refreshTokenRepository).revogarTodosDoUsuario(eq(userId), any());
    }

    @Test
    void trocaParaMesmaSenhaFalha() {
        UserEntity u = usuarioComSenha(passwordEncoder.encode("senhaCerta"));
        UUID userId = u.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.trocarSenha(userId, "senhaCerta", "senhaCerta"))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("diferente da atual");
    }
}
