package com.devsos.application.service;

import com.devsos.application.dto.session.SessionCreateRequestDTO;
import com.devsos.application.dto.session.SessionResponseDTO;
import com.devsos.application.dto.session.SessionUpdateStatusRequestDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.application.exception.ResourceNotFoundException;
import com.devsos.domain.post.PostEntity;
import com.devsos.domain.post.PostStatus;
import com.devsos.domain.session.SessionEntity;
import com.devsos.domain.session.SessionStatus;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.PostRepository;
import com.devsos.infrastructure.repository.SessionRepository;
import com.devsos.infrastructure.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.devsos.domain.session.SessionStatus.ACTIVE;
import static com.devsos.domain.session.SessionStatus.CANCELLED;
import static com.devsos.domain.session.SessionStatus.COMPLETED;
import static com.devsos.domain.session.SessionStatus.MATCHED;

/**
 * Serviço de "corridas" — o coração da dinâmica Uber do DevSOS.
 *
 * <h2>A máquina de estados da corrida</h2>
 * <pre>
 *   (aceite no service)   (início do chat)   (resolução entregue)
 *   POST /sessions        PATCH → ACTIVE      PATCH → COMPLETED
 *        │                     │                    │
 *        ▼                     ▼                    ▼
 *   [MATCHED] ───────────▶ [ACTIVE] ───────────▶ [COMPLETED]
 *        │                     │
 *        └──────── PATCH → CANCELLED <───────────┘
 *                    (post volta a OPEN)
 * </pre>
 *
 * <ul>
 *   <li><b>Transições</b>: ACTIVE exige MATCHED; COMPLETED exige ACTIVE e só o
 *       HELPER conclui; CANCELLED é livre para os dois lados (até ACTIVE).</li>
 *   <li><b>Pontos</b>: no COMPLETED, {@code recompensa_valor} sai do autor e
 *       entra no helper (só posts PAID). Validamos saldo suficiente antes.</li>
 *   <li><b>Post</b>: o post sai do feed ao aceitar (IN_PROGRESS), volta ao
 *       cancela (OPEN) e resolve ao concluir (RESOLVED).</li>
 * </ul>
 */
@Service
public class SessionService {

    private static final String RECURSO_CORRIDA = "Corrida";
    private static final String RECURSO_POST = "Post";
    private static final String RECURSO_USUARIO = "Usuário";

    private final SessionRepository sessionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public SessionService(SessionRepository sessionRepository,
                          PostRepository postRepository,
                          UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    /**
     * HELPER aceita o socorro de um post OPEN.
     *
     * <p>Três barreiras em sequência (camadas!):</p>
     * <ol>
     *   <li><b>Service</b>: post precisa estar OPEN, helper ≠ autor, e não pode
     *       existir outra corrida MATCHED/ACTIVE (1 por vez);</li>
     *   <li><b>Banco (trigger)</b>: {@code trg_sessions_no_self_help} veta o
     *       self-help mesmo se um cliente "malicioso" burlar a 1ª barreira;</li>
     *   <li><b>Banco (índice único)</b>: {@code uniq_sessions_post_active} veta
     *       a 2ª corrida ativa — proteção contra corrida de concorrência.</li>
     * </ol>
     */
    @Transactional
    public SessionResponseDTO aceitarSocorro(UUID helperId, SessionCreateRequestDTO request) {
        PostEntity post = postRepository.findById(request.postId())
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_POST));

        if (post.getStatus() != PostStatus.OPEN) {
            throw new RegraDeNegocioException(
                "Este post já está em atendimento ou não está mais aberto.");
        }
        if (post.getAuthor().getId().equals(helperId)) {
            throw new RegraDeNegocioException("Você não pode aceitar o próprio pedido de ajuda.");
        }
        if (sessionRepository.findByPostIdAndStatusIn(post.getId(), List.of(MATCHED, ACTIVE)).isPresent()) {
            throw new RegraDeNegocioException("Este post já tem uma corrida em andamento.");
        }

        UserEntity helper = userRepository.findById(helperId)
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_USUARIO));

        // O post sai do feed enquanto a corrida existe
        post.setStatus(PostStatus.IN_PROGRESS);
        postRepository.saveAndFlush(post);

        SessionEntity salva = sessionRepository.saveAndFlush(
            SessionEntity.aceitarSocorro(post, helper)
        );
        return SessionResponseDTO.from(salva);
    }

    /**
     * Avança a máquina de estados ({@code PATCH /api/sessions/{id}}).
     * Quem chama precisa participar da corrida (autor ou helper); a matriz de
     * transições e a regra "só o helper conclui" estão documentadas no header
     * da classe.
     */
    @Transactional
    public SessionResponseDTO atualizarStatus(UUID sessionId, UUID userId, SessionUpdateStatusRequestDTO request) {
        SessionEntity sessao = sessionRepository.findById(sessionId)
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_CORRIDA));

        PostEntity post = sessao.getPost();
        boolean eAutor = post.getAuthor().getId().equals(userId);
        boolean eHelper = sessao.getHelper().getId().equals(userId);
        if (!eAutor && !eHelper) {
            throw new RegraDeNegocioException("Você não participa desta corrida.");
        }

        SessionStatus atual = sessao.getStatus();
        SessionStatus alvo = request.status();

        if (alvo == atual) {
            throw new RegraDeNegocioException("A corrida já está com status " + atual + ".");
        }

        switch (alvo) {
            case ACTIVE -> {
                if (atual != MATCHED) {
                    throw new RegraDeNegocioException("Para ficar ACTIVE, a corrida precisa estar MATCHED.");
                }
            }
            case COMPLETED -> {
                if (atual != ACTIVE) {
                    throw new RegraDeNegocioException("Para concluir, a corrida precisa estar ACTIVE.");
                }
                if (!eHelper) {
                    throw new RegraDeNegocioException("Somente o helper pode marcar a corrida como concluída.");
                }
                completarCorrida(sessao, post);
            }
            case CANCELLED -> {
                if (atual == COMPLETED) {
                    throw new RegraDeNegocioException("Uma corrida concluída não pode ser cancelada.");
                }
                post.setStatus(PostStatus.OPEN);      // volta para a fila do feed
                postRepository.saveAndFlush(post);
            }
            default -> throw new RegraDeNegocioException(
                "Status inválido: aceitamos ACTIVE, COMPLETED ou CANCELLED (MATCHED só no aceite).");
        }

        sessao.setStatus(alvo);
        SessionEntity salva = sessionRepository.saveAndFlush(sessao);
        return SessionResponseDTO.from(salva);
    }

    /**
     * Conclusão: transfere os pontos e resolve o post.
     * <p>Regra: só posts {@code PAID} movimentam pontos. O autor precisa ter
     * saldo suficiente (a própria DDL tem {@code CHECK saldo_pontos >= 0}).</p>
     */
    private void completarCorrida(SessionEntity sessao, PostEntity post) {
        BigDecimal recompensa = post.getRecompensaValor();
        if (recompensa != null && recompensa.compareTo(BigDecimal.ZERO) > 0) {
            UserEntity autor = post.getAuthor();
            UserEntity helper = sessao.getHelper();
            int valor = recompensa.intValue();

            if (autor.getSaldoPontos() < valor) {
                throw new RegraDeNegocioException(
                    "O autor não tem saldo suficiente (" + valor + " pontos) para pagar a recompensa.");
            }
            autor.setSaldoPontos(autor.getSaldoPontos() - valor);
            helper.setSaldoPontos(helper.getSaldoPontos() + valor);
            userRepository.saveAll(List.of(autor, helper));
        }
        post.setStatus(PostStatus.RESOLVED);
        postRepository.saveAndFlush(post);
        sessao.setCompletedAt(Instant.now());
    }

    /** Corridas onde o usuário é autor ou helper (paginação). */
    @Transactional(readOnly = true)
    public PagedModel<SessionResponseDTO> listarMinhas(UUID userId, Pageable pageable) {
        Page<SessionResponseDTO> page = sessionRepository
            .findMinhasSessoes(userId, pageable)
            .map(SessionResponseDTO::from);
        return new PagedModel<>(page);
    }

    /** Detalhe de uma corrida — só para quem participa dela. */
    @Transactional(readOnly = true)
    public SessionResponseDTO detalhar(UUID sessionId, UUID userId) {
        SessionEntity sessao = sessionRepository.findById(sessionId)
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_CORRIDA));

        boolean participa = sessao.getPost().getAuthor().getId().equals(userId)
            || sessao.getHelper().getId().equals(userId);
        if (!participa) {
            throw new RegraDeNegocioException("Você não participa desta corrida.");
        }
        return SessionResponseDTO.from(sessao);
    }
}