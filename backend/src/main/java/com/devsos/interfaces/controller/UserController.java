package com.devsos.interfaces.controller;

import com.devsos.application.dto.user.AtualizarTecnologiasRequestDTO;
import com.devsos.application.dto.user.UserProfileResponseDTO;
import com.devsos.application.service.UserService;
import com.devsos.infrastructure.security.IdUsuarioLogado;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controlador REST do usuário.
 *
 * <h2>O papel do Controller (Responsabilidade Única / SRP)</h2>
 * O Controller é a "porta de entrada" da web: ele recebe a requisição HTTP,
 * convida o SERVICE a fazer o trabalho pesado e devolve a resposta. Ele NÃO
 * contém regra de negócio e NÃO fala com o banco — se precisar dessas
 * mudanças, o código será escrito na camada certa (Service/Repository).
 * <p>
 * DICA: para ter essa garantia de leitura, qualquer método aqui deve caber em
 * ~3 linhas: chamar o relacionado, montar o {@code ResponseEntity} e pronto.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** GET /api/users/{id} — retorna o perfil público de um usuário. */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponseDTO> buscarPerfil(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.buscarPerfilPorId(id));
    }

    /**
     * PATCH /api/users/{id}/technologies — atualiza tecnologias dominadas.
     * PATCH (não PUT) porque alteramos apenas um atributo do recurso.
     * <p><b>A partir da iteração de autenticação:</b> somente o PRÓPRIO usuário
     * logado pode mudar suas tecnologias. O id usado é o do TOKEN
     * ({@code @AuthenticationPrincipal}); o {@code id} do path é mantido no
     * contrato, mas o dono é quem manda verdade.</p>
     */
    @PatchMapping("/{id}/technologies")
    public ResponseEntity<UserProfileResponseDTO> atualizarTecnologias(
            @AuthenticationPrincipal IdUsuarioLogado logado,
            @Valid @RequestBody AtualizarTecnologiasRequestDTO request) {
        return ResponseEntity.ok(userService.atualizarTecnologias(logado.id(), request));
    }
}