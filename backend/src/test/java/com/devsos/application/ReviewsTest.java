package com.devsos.application;

import com.devsos.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #12 — Reviews: nota 1–5, uma por pessoa por corrida, só após COMPLETED,
 * média recalculada pelo trigger e reputação (GET /api/reviews).
 */
class ReviewsTest extends BaseIntegrationTest {

    private String github() {
        return "gh-" + UUID.randomUUID();
    }

    private UsuarioLei novoUsuario(String rol) {
        return criarUsuario(rol + " " + UUID.randomUUID().toString().substring(0, 6),
            UUID.randomUUID() + "@teste.com", github());
    }

    private UUID criarPost(UsuarioLei autor, String tipo, String recompensa) throws Exception {
        String body = """
            {"titulo":"Ajuda %s","descricao":"Preciso de ajuda com um bug estranho no meu codigo.",
             "tags":[],"tipo":"%s","recompensaValor":%s,"mediaUrl":""}
            """.formatted(UUID.randomUUID().toString().substring(0, 8), tipo, recompensa);
        var result = mockMvc.perform(post("/api/posts")
                .header("Authorization", bearer(autor))
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private UUID aceitar(UsuarioLei helper, UUID postId) throws Exception {
        var result = mockMvc.perform(post("/api/sessions")
                .header("Authorization", bearer(helper))
                .contentType("application/json")
                .content("{\"postId\":\"%s\"}".formatted(postId)))
            .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    /** Cria corrida e conclui, retornando o id da session COMPLETED. */
    private UUID corridaConcluida(UsuarioLei autor, UsuarioLei helper) throws Exception {
        UUID postId = criarPost(autor, "FREE", "0");
        UUID sessionId = aceitar(helper, postId);
        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(helper))
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(helper))
                .contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
            .andExpect(status().isOk());
        return sessionId;
    }

    @Test
    void avaliacaoRecalculaMediaDoAvaliado() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID sessionId = corridaConcluida(autor, helper);

        // autor avalia helper com nota 4 -> media do helper = 4.00
        mockMvc.perform(post("/api/reviews")
                .header("Authorization", bearer(autor))
                .contentType("application/json")
                .content("""
                    {"sessionId":"%s","nota":4,"comentario":"Ajudou bastante"}
                    """.formatted(sessionId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nota").value(4))
            .andExpect(jsonPath("$.reviewedId").value(helper.id().toString()))
            .andExpect(jsonPath("$.mediaAvaliacoesDoAvaliado").value(closeTo(4.0, 0.001)));

        // helper avalia autor com nota 5 -> media do autor = 5.00
        mockMvc.perform(post("/api/reviews")
                .header("Authorization", bearer(helper))
                .contentType("application/json")
                .content("""
                    {"sessionId":"%s","nota":5,"comentario":"Problema bem descrito"}
                    """.formatted(sessionId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reviewedId").value(autor.id().toString()))
            .andExpect(jsonPath("$.mediaAvaliacoesDoAvaliado").value(closeTo(5.0, 0.001)));
    }

    @Test
    void avaliacaoAntesDeConcluirRetorna400() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID postId = criarPost(autor, "FREE", "0");
        UUID sessionId = aceitar(helper, postId);

        mockMvc.perform(post("/api/reviews")
                .header("Authorization", bearer(autor))
                .contentType("application/json")
                .content("""
                    {"sessionId":"%s","nota":5,"comentario":"Cedo demais"}
                    """.formatted(sessionId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Só é possível avaliar depois que a corrida é concluída."));
    }

    @Test
    void segundaAvaliacaoDaMesmaPessoaRetorna400() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID sessionId = corridaConcluida(autor, helper);

        String body = """
            {"sessionId":"%s","nota":5,"comentario":"Boa"}
            """.formatted(sessionId);
        mockMvc.perform(post("/api/reviews").header("Authorization", bearer(autor))
                .contentType("application/json").content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reviews").header("Authorization", bearer(autor))
                .contentType("application/json").content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Você já avaliou esta corrida."));
    }

    @Test
    void notaForaDe1a5Retorna400ComFieldErrors() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID sessionId = corridaConcluida(autor, helper);

        mockMvc.perform(post("/api/reviews")
                .header("Authorization", bearer(autor))
                .contentType("application/json")
                .content("""
                    {"sessionId":"%s","nota":6,"comentario":"Nota inválida"}
                    """.formatted(sessionId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.nota").exists());
    }

    @Test
    void terceiroNaoAvalia() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID sessionId = corridaConcluida(autor, helper);

        var externo = novoUsuario("Externo");
        mockMvc.perform(post("/api/reviews")
                .header("Authorization", bearer(externo))
                .contentType("application/json")
                .content("""
                    {"sessionId":"%s","nota":5,"comentario":"Não participei"}
                    """.formatted(sessionId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Somente quem participou da corrida pode avaliar."));
    }

    @Test
    void reputacaoListaAvaliacoesRecebidas() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID sessionId = corridaConcluida(autor, helper);

        mockMvc.perform(post("/api/reviews").header("Authorization", bearer(autor))
                .contentType("application/json")
                .content("""
                    {"sessionId":"%s","nota":4,"comentario":"Bom"}
                    """.formatted(sessionId)))
            .andExpect(status().isCreated());

        // helper recebeu 1 avaliação
        mockMvc.perform(get("/api/reviews").header("Authorization", bearer(helper)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].nota").value(4));
    }
}