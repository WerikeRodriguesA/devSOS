package com.devsos.interfaces.controller;

import com.devsos.application.dto.post.PostCreateRequestDTO;
import com.devsos.application.dto.post.PostResponseDTO;
import com.devsos.application.service.PostService;
import com.devsos.domain.post.PostTipo;
import com.devsos.infrastructure.security.IdUsuarioLogado;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Controlador REST do feed de posts.
 *
 * <h2>Sobre o nome da rota</h2>
 * {@code POST /api/posts} e {@code GET /api/posts} são "routes RESTful":
 * o verbo HTTP diz a intenção (criar vs. listar) e o path o recurso (posts).
 * <p>
 * <b>201 Created</b>: quando uma publicação nasce, o código de status correto
 * é 201 (recurso criado), não 200. Repare também no {@code Location} no
 * cabeçalho — indica onde o novo recurso "mora".
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * GET /api/posts?page=0&size=10&sort=createdAt,desc
     * Feed paginado de posts OPEN, mais recentes primeiro.
     * <p>
     * Filtros opcionais e combináveis:
     * <ul>
     *   <li>{@code q} — busca por texto (título ou descrição, contém e
     *       case-insensitive);</li>
     *   <li>{@code tag} — filtra por uma tag (índice GIN);</li>
     *   <li>{@code tipo} — {@code FREE} ou {@code PAID}.</li>
     * </ul>
     * Ex.: {@code GET /api/posts?q=spring&tag=java&tipo=PAID&page=0&size=10}
     */
    @SecurityRequirements(value = {}) // feed é público como o Instagram
    @GetMapping
    public ResponseEntity<PagedModel<PostResponseDTO>> listarFeed(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "tipo", required = false) PostTipo tipo) {

        return ResponseEntity.ok(postService.listarFeed(pageable, q, tag, tipo));
    }

    /**
     * POST /api/posts — cria uma dúvida. {@code @Valid} aciona a validação do DTO.
     * <p>O autor não vem no corpo: quem cria o post é o usuário do TOKEN
     * ({@code @AuthenticationPrincipal} injeta o {@code IdUsuarioLogado}).</p>
     */
    @PostMapping
    public ResponseEntity<PostResponseDTO> criarPost(
            @AuthenticationPrincipal IdUsuarioLogado logado,
            @Valid @RequestBody PostCreateRequestDTO request) {

        PostResponseDTO created = postService.criar(logado.id(), request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/posts/" + created.id()))
            .body(created);
    }
}