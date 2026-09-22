package com.devsos.application;

import com.devsos.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #12 — Transferência de pontos numa corrida PAID concluída, e o guard
 * de saldo insuficiente.
 */
class PontosTest extends BaseIntegrationTest {

    private UsuarioLei novoUsuario(String rol) {
        return criarUsuario(rol + " " + UUID.randomUUID().toString().substring(0, 6),
            UUID.randomUUID() + "@teste.com", "gh-" + UUID.randomUUID());
    }

    private UUID criarPostPaid(UsuarioLei autor, int recompensa) throws Exception {
        String body = """
            {"titulo":"Ajuda paga %s","descricao":"Preciso de ajuda urgente com um bug de producao.",
             "tags":[],"tipo":"PAID","recompensaValor":%d,"mediaUrl":""}
            """.formatted(UUID.randomUUID().toString().substring(0, 8), recompensa);
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

    private void mudarStatus(UsuarioLei quem, UUID sessionId, String status, int esperado) throws Exception {
        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(quem))
                .contentType("application/json")
                .content("{\"status\":\"%s\"}".formatted(status)))
            .andExpect(status().is(esperado));
    }

    @Test
    void concluirCorridaPaidTransferePontosDoAutorParaOHelper() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        creditarSaldo(autor.id(), 100);

        UUID postId = criarPostPaid(autor, 50);
        UUID sessionId = aceitar(helper, postId);
        mudarStatus(helper, sessionId, "ACTIVE", 200);
        mudarStatus(helper, sessionId, "COMPLETED", 200);

        assertEquals(50, saldoDe(autor.id()), "autor paga a recompensa");
        assertEquals(50, saldoDe(helper.id()), "helper recebe a recompensa");
    }

    @Test
    void autorSemSaldoSuficienteRetorna400ENaoTransfere() throws Exception {
        var autor = novoUsuario("Autor");
        var helper = novoUsuario("Helper");
        creditarSaldo(autor.id(), 10);

        UUID postId = criarPostPaid(autor, 50);
        UUID sessionId = aceitar(helper, postId);
        mudarStatus(helper, sessionId, "ACTIVE", 200);

        mockMvc.perform(patch("/api/sessions/" + sessionId)
                .header("Authorization", bearer(helper))
                .contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("O autor não tem saldo suficiente (50 pontos) para pagar a recompensa."));

        assertEquals(10, saldoDe(autor.id()));
        assertEquals(0, saldoDe(helper.id()));
    }
}