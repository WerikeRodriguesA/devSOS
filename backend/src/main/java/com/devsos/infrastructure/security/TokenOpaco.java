package com.devsos.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Geração e "hash" de <b>tokens opacos</b> (refresh token, token de recuperação
 * de senha, etc.).
 *
 * <h2>Por que opaco + hash?</h2>
 * <p>Um token opaco é um valor aleatório sem significado, guardado no banco
 * apenas como {@code SHA-256} hex. Assim, um vazamento do banco <b>não</b>
 * libera tokens utilizáveis — mesmo princípio do BCrypt para senhas. O token
 * cru só existe na resposta/saída e no cliente.</p>
 *
 * <p>Extraído do {@code RefreshTokenService} para ser compartilhado com o
 * fluxo de recuperação de senha sem duplicar criptografia.</p>
 */
public final class TokenOpaco {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenOpaco() {
    }

    /** 32 bytes aleatórios → 43 chars base64url (256 bits de entropia). */
    public static String gerar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 do token cru, em hex minúsculo (64 chars). */
    public static String hash(String tokenCru) {
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
}
