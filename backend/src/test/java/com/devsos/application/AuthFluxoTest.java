package com.devsos.application;

import com.devsos.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #12 — Auth: register 201, duplicado 400, login 200, senha errada 400,
 * 401 sem token, padrão ApiError.
 */
class AuthFluxoTest extends BaseIntegrationTest {

    private String emailUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID() + "@teste.com";
    }

    @Test
    void registerRetorna201ComTokenERefresh() throws Exception {
        String email = emailUnico("ana");

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content("""
                    {"nome":"Ana Dev","email":"%s","senha":"senha123","githubUsername":"gh-ana-%s"}
                    """.formatted(email, UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token", not(emptyString())))
            .andExpect(jsonPath("$.usuario.email").value(email))
            .andExpect(jsonPath("$.usuario.nome").value("Ana Dev"))
            .andExpect(jsonPath("$.refreshToken", not(emptyString())));
    }

    @Test
    void registerComEmailDuplicadoRetorna400() throws Exception {
        String email = emailUnico("dupe");
        String body = """
            {"nome":"Clara Dev","email":"%s","senha":"senha123","githubUsername":"gh-clara-%s"}
            """.formatted(email, UUID.randomUUID());

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content("""
                    {"nome":"Clara Re","email":"%s","senha":"senha123","githubUsername":"gh-clara2-%s"}
                    """.formatted(email, UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Já existe uma conta com este e-mail."));
    }

    @Test
    void loginRetorna200ComToken() throws Exception {
        var u = criarUsuario("Bia", emailUnico("bia"), "gh-bia-" + UUID.randomUUID());

        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("""
                    {"email":"%s","senha":"%s"}
                    """.formatted(u.email(), SENHA_PADRAO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", not(emptyString())))
            .andExpect(jsonPath("$.usuario.email").value(u.email()));
    }

    @Test
    void loginComSenhaErradaRetorna400ComMensagemNaoReveladora() throws Exception {
        var u = criarUsuario("Dan", emailUnico("dan"), "gh-dan-" + UUID.randomUUID());

        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("""
                    {"email":"%s","senha":"senhaErrada"}
                    """.formatted(u.email())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
    }

    @Test
    void loginDeEmailInexistenteRetorna400MesmaMensagem() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("""
                    {"email":"nao-existe-%s@teste.com","senha":"senha123"}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
    }

    @Test
    void rotaProtegidaSemTokenRetorna401NoFormatoApiError() throws Exception {
        mockMvc.perform(get("/api/sessions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("Não autenticado. Envie o token no cabeçalho Authorization."));
    }

    @Test
    void rotaProtegidaComTokenValidoPassa() throws Exception {
        var u = criarUsuario("Eli", emailUnico("eli"), "gh-eli-" + UUID.randomUUID());

        mockMvc.perform(get("/api/sessions").header("Authorization", bearer(u)))
            .andExpect(status().isOk());
    }
}