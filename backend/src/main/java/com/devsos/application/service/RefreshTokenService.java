package com.devsos.application.service;

import com.devsos.application.dto.auth.AuthResponseDTO;
import com.devsos.application.dto.user.UserProfileResponseDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.application.exception.ResourceNotFoundException;
import com.devsos.domain.refresh.RefreshTokenEntity;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.RefreshTokenRepository;
import com.devsos.infrastructure.repository.UserRepository;
import com.devsos.infrastructure.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Ciclo de vida do refresh token: emissão, rotação, reemissão e revogação
 * (logout / logout forçado).
 *
 * <h2>Fluxo</h2>
 * <pre>
 *   login/register ──▶ access JWT (curto) + refresh token (opaco, TTL longo)
 *                              │                    │
 *        access expira? ──────▶ └── POST /api/auth/refresh ──▶ NOVO par
 *                                                            (roda o antigo)
 *   logout (token) ──▶ revoga só este                          logout forçado
 *   logout (vazio) ──▶ revoga TODOS do usuário                    (sem token)
 * </pre>
 *
 * <h2>Por que armazenar HASH e não o token?</h2>
 * Assim como só guardamos o hash da senha (BCrypt), o refresh token cru nunca
 * deve morar no banco: guardamos o SHA-256 HEX. Se o banco vazar, o atacante
 * só encontra hashes inúteis (SHA-256 é unidirecional).
 *
 * <h2>Rotação e detecção de reuso</h2>
 * A cada {@code /refresh} o token usado é marcado como revogado e nasce um
 * novo ({@code replacedBy} mantém o rastro). Se um token JÁ revogado aparecer
 * de novo, a resposta é interpretada como roubo (o token vazou e foi usado
 * por um segundo agente) → revogamos a família inteira do usuário.
 */
@Service
public class RefreshTokenService {

    /** Gerador criptograficamente seguro para os tokens opacos. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final MessageDigest sha256;
    private final long expiracaoSegundos;

    /**
     * Transação isolada (REQUIRES_NEW) usada na revogação por suspeita de
     * reuso: ela PRECISA persistir mesmo que a transação do erro acima
     * (a que lança a exceção) sofra rollback.
     */
    private final TransactionTemplate txIsolada;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtService jwtService,
                               PlatformTransactionManager txManager,
                               @Value("${devsos.jwt.refresh-expiracao-segundos}") long expiracaoSegundos) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.sha256 = novoDigest();
        this.expiracaoSegundos = expiracaoSegundos;
        this.txIsolada = new TransactionTemplate(txManager);
        this.txIsolada.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public long getExpiracaoSegundos() {
        return expiracaoSegundos;
    }

    /**
     * Emite um par de credenciais: access JWT + refresh token persistido.
     * Usado no login e no cadastro (o cadastro já autentica).
     */
    @Transactional
    public AuthResponseDTO emitirTokens(UserEntity usuario) {
        String tokenCru = gerarTokenCru();
        refreshTokenRepository.save(new RefreshTokenEntity(usuario.getId(), hashDoToken(tokenCru), expiracaoSegundos));
        limparExpirados();
        return montarResposta(usuario, tokenCru);
    }

    /**
     * Troca um refresh token válido por um par novo (o antigo morre).
     *
     * <p>Regras:</p>
     * <ul>
     *   <li>token inexistente ⇒ {@code 400 "Refresh token inválido."};</li>
     *   <li>token expirado/revogado ⇒ {@code 400} + <b>revoga a família toda</b>
     *       (detecção de reuso) — quem reutiliza um token que já virou lixo
     *       provavelmente não é o dono;</li>
     *   <li>ok ⇒ access JWT novo + refresh token novo (rotacionado).</li>
     * </ul>
     */
    @Transactional
    public AuthResponseDTO reemitirTokens(String tokenCru) {
        RefreshTokenEntity atual = refreshTokenRepository.findByTokenHash(hashDoToken(tokenCru))
            .orElseThrow(() -> new RegraDeNegocioException("Refresh token inválido."));

        if (atual.isRevogado() || atual.estaExpirado()) {
            // Suspeita de roubo/reuso: revoga a família TODA do usuário. A
            // revogação roda em transação ISOLADA (REQUIRES_NEW) para não ser
            // desfeita pelo rollback da exceção lançada logo abaixo.
            txIsolada.executeWithoutResult(tx -> refreshTokenRepository.revogarTodosDoUsuario(atual.getUserId(), Instant.now()));
            throw new RegraDeNegocioException(
                "Refresh token reutilizado (ou expirado). Todas as sessões foram revogadas — faça login novamente.");
        }

        UserEntity usuario = userRepository.findById(atual.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        String novoToken = gerarTokenCru();
        RefreshTokenEntity substituto = refreshTokenRepository.save(
            new RefreshTokenEntity(usuario.getId(), hashDoToken(novoToken), expiracaoSegundos));
        atual.revogar(substituto.getId()); // rotação: o antigo chega ao fim já revogado
        refreshTokenRepository.save(atual);
        limparExpirados();

        return montarResposta(usuario, novoToken);
    }

    /**
     * Revoga refresh tokens.
     *
     * <ul>
     *   <li>{@code tokenCru == null / blank} → <b>logout forçado</b>: revoga
     *       TODOS os refresh tokens do usuário (todas as "sessões");</li>
     *   <li>{@code tokenCru} informado → revoga SÓ aquele (logout deste
     *       dispositivo), validando que ele pertence ao usuário do JWT.</li>
     * </ul>
     */
    @Transactional
    public void revogar(UUID userId, String tokenCru) {
        if (tokenCru == null || tokenCru.isBlank()) {
            refreshTokenRepository.revogarTodosDoUsuario(userId, Instant.now());
        } else {
            RefreshTokenEntity alvo = refreshTokenRepository.findByTokenHash(hashDoToken(tokenCru))
                .orElseThrow(() -> new RegraDeNegocioException("Refresh token não encontrado."));
            if (!userId.equals(alvo.getUserId())) {
                throw new RegraDeNegocioException("Refresh token não pertence a este usuário.");
            }
            alvo.revogar(null);
            refreshTokenRepository.save(alvo);
        }
        limparExpirados();
    }

    // ------------------------------------------------------------------ helpers

    /** 32 bytes aleatórios → 43 chars base64url: 256 bits de entropia. */
    private static String gerarTokenCru() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashDoToken(String tokenCru) {
        byte[] digest = novoDigest().digest(tokenCru.getBytes(StandardCharsets.UTF_8));
        return hexOf(digest);
    }

    private static MessageDigest novoDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM sem SHA-256", ex);
        }
    }

    private static String hexOf(byte[] bytes) {
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16))
              .append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private AuthResponseDTO montarResposta(UserEntity usuario, String tokenCru) {
        String accessToken = jwtService.gerarToken(usuario.getId(), usuario.getEmail(), usuario.getNome());
        return new AuthResponseDTO(
            accessToken,
            jwtService.getExpiracaoSegundos(),
            UserProfileResponseDTO.from(usuario),
            tokenCru,
            expiracaoSegundos
        );
    }

    /** Higiene simples: tokens vencidos não acumulam no banco. */
    private void limparExpirados() {
        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now().minusSeconds(1));
    }
}