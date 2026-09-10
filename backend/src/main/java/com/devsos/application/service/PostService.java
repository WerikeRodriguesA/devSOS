package com.devsos.application.service;

import com.devsos.application.dto.post.PostCreateRequestDTO;
import com.devsos.application.dto.post.PostResponseDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.application.exception.ResourceNotFoundException;
import com.devsos.domain.post.PostEntity;
import com.devsos.domain.post.PostStatus;
import com.devsos.domain.post.PostTipo;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.PostRepository;
import com.devsos.infrastructure.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Serviço de posts: regras de negócio do feed.
 * <p>
 * Aqui mora a lógica que NÃO pode viver no Controller nem no Repository:
 * todos os caminhos de entrada (mobile, web, testes) passam pelo MESMO
 * Service — a regra é válida em qualquer lugar. (Princípio do contrato
 * único / consistência.)
 */
@Service
public class PostService {

    private static final String RECURSO_POST = "Post";

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostService(PostRepository postRepository, UserRepository userRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    /**
     * Fábrica de {@code Sort} reutilizável: sempre "mais recente primeiro".
     * Isola o nome do campo ({@code createdAt}) num só lugar.
     */
    public static Sort feedSort() {
        return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    /**
     * REGRA DE NEGÓCIO principal de criação:
     * <ol>
     *   <li>Valida o autor (404 se não existir);</li>
     *   <li>Aplica a regra de recompensa:
     *       <ul>
     *         <li>post {@code FREE} ⇒ recompensa OBRIGATORIAMENTE 0;</li>
     *         <li>post {@code PAID} ⇒ recompensa OBRIGATORIAMENTE &gt; 0.</li>
     *       </ul>
     *       (validação idêntica à {@code posts_clean_reward} do banco);</li>
     *   <li>Persiste e devolve o DTO de resposta com HTTP 201 no Controller.</li>
     * </ol>
     * <p>O {@code authorId} é recebido do TOKEN (usuário autenticado), e o
     * {@code status}/{@code createdAt} são definidos pela entidade
     * ({@code PostEntity.criar()}), nunca pelo cliente.
     */
    @Transactional
    public PostResponseDTO criar(UUID authorId, PostCreateRequestDTO request) {
        UserEntity author = userRepository.findById(authorId)
            .orElseThrow(() -> ResourceNotFoundException.of("Usuário autor"));

        validarRecompensa(request.tipo(), request.recompensaValor());

        PostEntity post = PostEntity.criar(
            author,
            request.titulo().trim(),
            request.descricao().trim(),
            request.mediaUrl() == null ? "" : request.mediaUrl().trim(),
            request.tags() == null ? List.of() : request.tags(),
            request.tipo(),
            request.recompensaValor() == null ? BigDecimal.ZERO : request.recompensaValor()
        );

        return PostResponseDTO.from(postRepository.saveAndFlush(post));
    }

    /**
     * Feed principal paginado: posts com status OPEN, dos mais recentes para
     * os mais antigos. O {@code Pageable} vem do Controller (parâmetros
     * ?page=0&size=10).
     */
    @Transactional(readOnly = true)
    public PagedModel<PostResponseDTO> listarFeed(Pageable pageable) {
        Page<PostResponseDTO> page = postRepository
            .findByStatus(PostStatus.OPEN, pageable)
            .map(PostResponseDTO::from);

        return new PagedModel<>(page);
    }

    private void validarRecompensa(PostTipo tipo, BigDecimal recompensaValor) {
        boolean isPaid = tipo == PostTipo.PAID;
        BigDecimal valor = recompensaValor == null ? BigDecimal.ZERO : recompensaValor;

        if (tipo == PostTipo.FREE && valor.compareTo(BigDecimal.ZERO) != 0) {
            throw new RegraDeNegocioException(
                "Post gratuito (FREE) não pode ter recompensa. Envie recompensaValor = 0.");
        }
        if (isPaid && valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException(
                "Post pago (PAID) exige recompensa maior que zero.");
        }
    }
}