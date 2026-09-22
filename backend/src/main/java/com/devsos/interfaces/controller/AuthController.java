package com.devsos.interfaces.controller;

import com.devsos.application.dto.auth.AuthResponseDTO;
import com.devsos.application.dto.auth.ChangePasswordRequestDTO;
import com.devsos.application.dto.auth.ForgotPasswordRequestDTO;
import com.devsos.application.dto.auth.ForgotPasswordResponseDTO;
import com.devsos.application.dto.auth.LoginRequestDTO;
import com.devsos.application.dto.auth.LogoutRequestDTO;
import com.devsos.application.dto.auth.RefreshTokenRequestDTO;
import com.devsos.application.dto.auth.RegistroRequestDTO;
import com.devsos.application.dto.auth.ResetPasswordRequestDTO;
import com.devsos.application.service.AuthService;
import com.devsos.application.service.PasswordResetService;
import com.devsos.application.service.RefreshTokenService;
import com.devsos.infrastructure.security.IdUsuarioLogado;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticação: cria conta, faz login, renova o access (refresh) e desloga
 * (revogando refresh tokens).
 *
 * <p><b>Públicas</b> (definido no {@code SecurityConfig}): {@code /register},
 * {@code /login} e {@code /refresh} — o cliente não tem token ainda quando se
 * cadastra, loga ou renova. {@code /logout} exige JWT porque precisa saber DE
 * quem revogar.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService,
                          RefreshTokenService refreshTokenService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
    }

    /** POST /api/auth/register — cria a conta e devolve access + refresh. */
    @SecurityRequirements(value = {}) // endpoint público: não exige JWT
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> registrar(@Valid @RequestBody RegistroRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
    }

    /** POST /api/auth/login — valida credenciais e devolve access + refresh. */
    @SecurityRequirements(value = {}) // endpoint público: não exige JWT
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /api/auth/refresh — troca um refresh token válido por um par novo
     * (access JWT + refresh rotacionado). O token usado morre no processo.
     */
    @SecurityRequirements(value = {}) // endpoint público: não exige JWT
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        return ResponseEntity.ok(refreshTokenService.reemitirTokens(request.refreshToken()));
    }

    /**
     * POST /api/auth/logout — revoga refresh tokens.
     * <ul>
     *   <li>corpo vazio → <b>logout forçado</b>: revoga TODAS as sessões do
     *       usuário do JWT;</li>
     *   <li>{@code {"refreshToken": "..."}} → revoga só aquele dispositivo.</li>
     * </ul>
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal IdUsuarioLogado usuarioLogado,
                                       @RequestBody(required = false) LogoutRequestDTO request) {
        String token = request == null ? null : request.refreshToken();
        refreshTokenService.revogar(usuarioLogado.id(), token);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/auth/forgot-password — inicia a recuperação de senha.
     * Resposta <b>sempre neutra</b> (não revela se o e-mail existe).
     */
    @SecurityRequirements(value = {}) // endpoint público: não exige JWT
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponseDTO> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request) {
        String tokenDev = passwordResetService.solicitarRecuperacao(request.email());
        return ResponseEntity.accepted().body(new ForgotPasswordResponseDTO(
            "Se este e-mail estiver cadastrado, enviaremos as instruções de recuperação.",
            tokenDev));
    }

    /**
     * POST /api/auth/reset-password — redefine a senha com o token recebido.
     * O token é de uso único e expira; ao redefinir, as sessões antigas caem.
     */
    @SecurityRequirements(value = {}) // endpoint público: não exige JWT
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        passwordResetService.redefinirSenha(request.token(), request.novaSenha());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/auth/change-password — troca a senha do usuário logado.
     * Exige a senha atual correta; as sessões antigas são revogadas.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal IdUsuarioLogado usuarioLogado,
                                               @Valid @RequestBody ChangePasswordRequestDTO request) {
        passwordResetService.trocarSenha(usuarioLogado.id(), request.senhaAtual(), request.novaSenha());
        return ResponseEntity.noContent().build();
    }
}