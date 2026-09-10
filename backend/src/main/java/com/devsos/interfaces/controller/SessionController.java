package com.devsos.interfaces.controller;

import com.devsos.application.dto.chat.ChatMessageDTO;
import com.devsos.application.dto.session.SessionCreateRequestDTO;
import com.devsos.application.dto.session.SessionResponseDTO;
import com.devsos.application.dto.session.SessionUpdateStatusRequestDTO;
import com.devsos.application.service.ChatMessageService;
import com.devsos.application.service.SessionService;
import com.devsos.infrastructure.security.IdUsuarioLogado;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Corridas ("chamadas de ajuda" estilo Uber).
 * <p>Todo endpoint exige JWT: o usuário logado é o helper no aceite e o
 * "participante" que navega pelo histórico. Quem não participa não vê nada.</p>
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final ChatMessageService chatMessageService;

    public SessionController(SessionService sessionService,
                             ChatMessageService chatMessageService) {
        this.sessionService = sessionService;
        this.chatMessageService = chatMessageService;
    }

    /** POST /api/sessions — o helper aceita o socorro de um post OPEN. */
    @PostMapping
    public ResponseEntity<SessionResponseDTO> aceitarSocorro(
            @AuthenticationPrincipal IdUsuarioLogado logado,
            @Valid @RequestBody SessionCreateRequestDTO request) {
        SessionResponseDTO criada = sessionService.aceitarSocorro(logado.id(), request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/sessions/" + criada.id()))
            .body(criada);
    }

    /** PATCH /api/sessions/{id} — avança o status (ACTIVE | COMPLETED | CANCELLED). */
    @PatchMapping("/{id}")
    public ResponseEntity<SessionResponseDTO> atualizarStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal IdUsuarioLogado logado,
            @Valid @RequestBody SessionUpdateStatusRequestDTO request) {
        return ResponseEntity.ok(sessionService.atualizarStatus(id, logado.id(), request));
    }

    /** GET /api/sessions — minhas corridas (como autor ou helper), paginado. */
    @GetMapping
    public ResponseEntity<PagedModel<SessionResponseDTO>> listarMinhas(
            @AuthenticationPrincipal IdUsuarioLogado logado,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(sessionService.listarMinhas(logado.id(), pageable));
    }

    /** GET /api/sessions/{id} — detalhe de uma corrida (só para participantes). */
    @GetMapping("/{id}")
    public ResponseEntity<SessionResponseDTO> detalhar(
            @PathVariable UUID id,
            @AuthenticationPrincipal IdUsuarioLogado logado) {
        return ResponseEntity.ok(sessionService.detalhar(id, logado.id()));
    }

    /**
     * GET /api/sessions/{id}/messages — histórico da sala da corrida.
     * Só participantes (autor ou helper) conseguem listar. A SALA em que a
     * corrida é "quem está dentro do WhatsApp"; terceiro não lê nem por engano.
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<List<ChatMessageDTO>> historicoMensagens(
            @PathVariable UUID id,
            @AuthenticationPrincipal IdUsuarioLogado logado) {
        return ResponseEntity.ok(chatMessageService.historico(id, logado.id()));
    }
}