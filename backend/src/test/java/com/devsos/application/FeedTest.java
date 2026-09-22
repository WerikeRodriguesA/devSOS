package com.devsos.application;

import com.devsos.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #12 — Feed: GET público, criar post FREE/PAID, validações 400 com
 * {@code fieldErrors}, paginação e ordenação.
 */
class FeedTest extends BaseIntegrationTest {

    private String tituloValido() {
        return "Bug estranho " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String descricaoValida() {
        return "Descrição detalhada do problema para o feed de testes automatizados.";
    }

    private String github() {
        return "gh-" + UUID.randomUUID();
    }

    @Test
    void feedPublicoSemTokenRetorna200() throws Exception {
        mockMvc.perform(get("/api/posts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void criarPostFreeRetorna201ComLocation() throws Exception {
        var u = criarUsuario("Free User", UUID.randomUUID() + "@teste.com", github());
        String body = """
            {"titulo":"%s","descricao":"%s","tags":["java"],"tipo":"FREE","recompensaValor":0,"mediaUrl":""}
            """.formatted(tituloValido(), descricaoValida());

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(u))
                .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/posts/")))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.tipo").value("FREE"))
            .andExpect(jsonPath("$.recompensaValor").value(0));
    }

    @Test
    void criarPostPaidRetorna201ComRecompensa() throws Exception {
        var u = criarUsuario("Paid User", UUID.randomUUID() + "@teste.com", github());
        String body = """
            {"titulo":"%s","descricao":"%s","tags":[],"tipo":"PAID","recompensaValor":50.00,"mediaUrl":""}
            """.formatted(tituloValido(), descricaoValida());

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(u))
                .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("PAID"))
            .andExpect(jsonPath("$.recompensaValor").value(50.0));
    }

    @Test
    void criarPostSemTokenRetorna401() throws Exception {
        String body = """
            {"titulo":"%s","descricao":"%s","tags":[],"tipo":"FREE","recompensaValor":0}
            """.formatted(tituloValido(), descricaoValida());

        mockMvc.perform(post("/api/posts").contentType("application/json").content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void criarPostComValidacaoInvalidaRetorna400ComFieldErrors() throws Exception {
        var u = criarUsuario("Validate Me", UUID.randomUUID() + "@teste.com", github());
        String body = """
            {"titulo":"ab","descricao":"curta","tags":[],"tipo":"FREE","recompensaValor":0}
            """;

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(u))
                .contentType("application/json").content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.fieldErrors.titulo").exists())
            .andExpect(jsonPath("$.fieldErrors.descricao").exists());
    }

    @Test
    void freeComRecompensaRetorna400() throws Exception {
        var u = criarUsuario("Free Inválido", UUID.randomUUID() + "@teste.com", github());
        String body = """
            {"titulo":"%s","descricao":"%s","tags":[],"tipo":"FREE","recompensaValor":10,"mediaUrl":""}
            """.formatted(tituloValido(), descricaoValida());

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(u))
                .contentType("application/json").content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Post gratuito (FREE) não pode ter recompensa. Envie recompensaValor = 0."));
    }

    @Test
    void paidSemRecompensaRetorna400() throws Exception {
        var u = criarUsuario("Paid Inválido", UUID.randomUUID() + "@teste.com", github());
        String body = """
            {"titulo":"%s","descricao":"%s","tags":[],"tipo":"PAID","recompensaValor":0,"mediaUrl":""}
            """.formatted(tituloValido(), descricaoValida());

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(u))
                .contentType("application/json").content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Post pago (PAID) exige recompensa maior que zero."));
    }

    @Test
    void feedPaginaSomentePostsOpen() throws Exception {
        var u = criarUsuario("Feed Autor", UUID.randomUUID() + "@teste.com", github());
        String termo = "feed" + UUID.randomUUID().toString().replace("-", "");
        for (int i = 0; i < 5; i++) {
            String body = """
                {"titulo":"%s %d","descricao":"%s","tags":[],"tipo":"FREE","recompensaValor":0,"mediaUrl":""}
                """.formatted(termo, i, descricaoValida());
            mockMvc.perform(post("/api/posts").header("Authorization", bearer(u))
                .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        }

        // o banco é compartilhado entre as classes de teste; filtrar pelo termo
        // único garante que só os posts deste teste entram na contagem.
        mockMvc.perform(get("/api/posts").param("q", termo).param("page", "0").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(5)));
    }
}