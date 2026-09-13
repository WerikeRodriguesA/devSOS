package com.devsos.infrastructure.security;

import com.devsos.application.exception.RegraDeNegocioException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Instant;
import java.util.UUID;

/**
 * Emite e valida tokens JWT (JSON Web Token).
 *
 * <h2>Anatomia do token</h2>
 * Um JWT tem três partes separadas por ponto ({@code header.payload.sigatura}):
 * <ol>
 *   <li><b>Header:</b> algoritmo sign(string: HS256) e tipo (JWT);</li>
 *   <li><b>Payload:</b>  as CLAIMS (reivindicações) — quem é o usuário
 *       ({@code sub}), quando o token expira ({@code exp}) etc.;</li>
 *   <li><b>Assinatura:</b>  hash do header+payload com a chave secreta — é o
 *       que impede alguém de forjar um token ("eu sou o Fulano") sem a chave.</li>
 * </ol>
 *
 * <h2>Por que expiração?</h2>
 * Se um token nunca expirasse e vazasse, o atacante teria acesso eterno.
 * Por isso geramos um tempo de vida curto ({@code jwt.expiracao-segundos}) e
 * exigimos o token no cabeçalho {@code Authorization: Bearer <token>}.
 * Quando ele expira, o cliente renova via refresh token
 * ({@code POST /api/auth/refresh}) — veja {@code RefreshTokenService}.
 * (O refresh token é o lado REVOGÁVEL da autenticação; um JWT em si é
 * stateless e não pode ser revogado antes de expirar.)
 */
@Service
public class JwtService {

    /** Claim padrão que identifica o dono do token (no jjwt). */
    private static final String CLAIM_SUBJECT = "sub";
    /** Claim customizada: e-mail do usuário autenticado. */
    private static final String CLAIM_EMAIL = "email";
    /** Claim customizada: nome do usuário autenticado. */
    private static final String CLAIM_NOME = "nome";

    private final SecretKey chave;
    private final long expiracaoSegundos;

    public JwtService(@Value("${devsos.jwt.secret}") String secret,
                      @Value("${devsos.jwt.expiracao-segundos}") long expiracaoSegundos) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        this.chave = Keys.hmacShaKeyFor(bytes);
        this.expiracaoSegundos = expiracaoSegundos;
    }

    public long getExpiracaoSegundos() {
        return expiracaoSegundos;
    }

    /**
     * Gera o token para um usuário autenticado.
     * {@code issuedAt} (iats) = agora; {@code expiration} = agora + expiracaoSegundos.
     */
    public String gerarToken(UUID id, String email, String nome) {
        Instant agora = Instant.now();
        Instant expiracao = agora.plusSeconds(expiracaoSegundos);

        return Jwts.builder()
            .subject(id.toString())
            .claim(CLAIM_EMAIL, email)
            .claim(CLAIM_NOME, nome)
            .issuedAt(Date.from(agora))
            .expiration(Date.from(expiracao))
            .signWith(chave)
            .compact();
    }

    /**
     * Valida o token e devolve o {@link IdUsuarioLogado} contido nele.
     * Se o token estiver expirado, malformado ou com chave errada, o jjwt
     * lança exceção → convertida aqui em erro de negócio (resposta 400).
     */
    public IdUsuarioLogado validarToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            return new IdUsuarioLogado(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NOME, String.class)
            );
        } catch (Exception ex) {
            throw new RegraDeNegocioException("Token inválido ou expirado. Faça login novamente.");
        }
    }
}