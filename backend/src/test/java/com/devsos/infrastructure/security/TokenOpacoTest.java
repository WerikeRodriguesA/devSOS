package com.devsos.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenOpacoTest {

    @Test
    void geraTokensAleatoriosDe43Chars() {
        String a = TokenOpaco.gerar();
        String b = TokenOpaco.gerar();

        assertThat(a).hasSize(43).isNotEqualTo(b);
        assertThat(a).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void hashEhSha256HexDeterministico() {
        String hash = TokenOpaco.hash("token-cru");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(TokenOpaco.hash("token-cru")).isEqualTo(hash);
        assertThat(TokenOpaco.hash("outro")).isNotEqualTo(hash);
    }
}
