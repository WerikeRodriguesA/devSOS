package com.devsos.application;

import com.devsos.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #12 — Corrida: aceite (MATCHED), duplicata, self-help, máquina de
 * estados (ACTIVE/COMPLETED/CANCELLED) e regras de participação.
 */
class CorridaTest extends BaseIntegrationTest {

    private String github() {
        return "gh-" + UUID.randomUUID();
    }

    private UsuarioLei novoUsuario(String rol) {
        return criarUsuario(rol + " " + UUID.randomUUID().toString().substring(0, 6),
            UUID.randomUUID() + "@teste.com", github());
    }

    private UUID criarPost(UsuarioLei autor, String tipo, String recompensa) throws Exception {
        String body = """
            {"titulo":"Necessito de ajuda %s","descricao":"Preciso de ajuda com um bug estranho no meu codigo.",
             "tags":["java"],"tipo":"%s","recompensaValor":%s,"mediaUrl":""}
            """.formatted(UUID.randomUUID().toString().substring(0, 8), tipo, recompensa);
        var result = mockMvc.perform(post("/api/posts")
                .header("Authorization", bearer(autor))
                .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private UUID aceitar(UsuarioLei helper, UUID postId) throws Exception {
        var result = mockMvc.perform(post("/api/sessions")
                .header("Authorization", bearer(helper))
                .contentType("application/json")
                .content("{\"postId\":\"%s\"}".formatted(postId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("MATCHED"))
            .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    @Test
    void fluxoCompletoAteConcluir() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID postId = criarPost(autor, "FREE", "0");
        UUID sessionId = aceitar(helper, postId);

        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(helper))
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(helper))
                .contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.completedAt", notNullValue()));
    }

    @Test
    void autorTentarAceitarProprioPostRetorna400() throws Exception {
        var autor = novoUsuario("Autor");
        UUID postId = criarPost(autor, "FREE", "0");

        mockMvc.perform(post("/api/sessions")
                .header("Authorization", bearer(autor))
                .contentType("application/json")
                .content("{\"postId\":\"%s\"}".formatted(postId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Você não pode aceitar o próprio pedido de ajuda."));
    }

    @Test
    void aceiteDuplicadoRetorna400() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID postId = criarPost(autor, "FREE", "0");
        aceitar(helper, postId);

        var helper2 = novoUsuario("Helper2");
        mockMvc.perform(post("/api/sessions")
                .header("Authorization", bearer(helper2))
                .contentType("application/json")
                .content("{\"postId\":\"%s\"}".formatted(postId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Este post já está em atendimento ou não está mais aberto."));
    }

    @Test
    void naoParticipanteNaoDetalha() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID postId = criarPost(autor, "FREE", "0");
        UUID sessionId = aceitar(helper, postId);

        var externo = novoUsuario("Externo");
        mockMvc.perform(get("/api/sessions/" + sessionId)
                .header("Authorization", bearer(externo)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Você não participa desta corrida."));
    }

    @Test
    void somenteHelperPodeConcluir() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID postId = criarPost(autor, "FREE", "0");
        UUID sessionId = aceitar(helper, postId);

        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(helper))
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(autor))
                .contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Somente o helper pode marcar a corrida como concluída."));
    }

    @Test
    void cancelarReabreOPost() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        UUID postId = criarPost(autor, "FREE", "0");
        UUID sessionId = aceitar(helper, postId);

        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(helper))
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
            .andExpect(status().isOk());

        var helper2 = novoUsuario("Helper2");
        aceitar(helper2, postId);
    }

    @Test
    void corridaConcluidaNaoCancela() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
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

        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(helper))
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Uma corrida concluída não pode ser cancelada."));
    }
}