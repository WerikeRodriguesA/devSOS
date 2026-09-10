package com.devsos.application.service;

import com.devsos.application.dto.chat.ChatMessageDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.application.exception.ResourceNotFoundException;
import com.devsos.domain.chat.ChatMessageEntity;
import com.devsos.domain.chat.ChatMessageType;
import com.devsos.domain.session.SessionEntity;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.ChatMessageRepository;
import com.devsos.infrastructure.repository.SessionRepository;
import com.devsos.infrastructure.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Serviço das mensagens do chat — o "protocolo" por trás de cada bolha.
 *
 * <h2>Por que existir (e não tudo dentro do controller de mensagem)?</h2>
 * O {@code @MessageMapping} do WebSocket é só a ANTENA: recebe a mensagem e
 * publica no tópico. Toda a regra (quem pode falar, persistir, montar a
 * resposta) mora aqui, no Service — no padrão do projeto (Controller enxuto,
 * Service com as regras), e porque o MESMO serviço é aproveitado no histórico
 * REST ({@code GET /api/sessions/{id}/messages}).
 *
 * <h2>O que é validado em cada envio?</h2>
 * <ol>
 *   <li>A sala precisa existir (existe corrida com esse {@code chatRoomId});</li>
 *   <li>O remetente precisa participar dessa corrida (autor ou helper);</li>
 *   <li>Regras leves de conteúdo (não vira "spam" em branco).</li>
 * </ol>
 * <p>O interceptor de canal ({@code ChatChannelInterceptor}) já bloqueia quem
 * não participa ANTES da mensagem chegar aqui. Revalidar no service é "defesa
 * em profundidade": se amanhã alguém mudar o interceptor, o chat não fica
 * aberto.</p>
 */
@Service
public class ChatMessageService {

    private static final String RECURSO_USUARIO = "Usuário";
    private static final int MAX_CONTEUDO = 10_000;

    private final SessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    public ChatMessageService(SessionRepository sessionRepository,
                              ChatMessageRepository chatMessageRepository,
                              UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
    }

    /**
     * Registra (se for texto/código) e prepara a mensagem para ser publicada
     * no tópico da sala. O {@code timestamp} é GRAVADO AQUI (relógio do
     * servidor), nunca o do cliente.
     */
    @Transactional
    public ChatMessageDTO registrar(String chatRoomId, UUID senderId, ChatMessageDTO entrada) {
        SessionEntity sessao = buscarSalaParticipada(chatRoomId, senderId);

        UserEntity sender = userRepository.findById(senderId)
            .orElseThrow(() -> ResourceNotFoundException.of(RECURSO_USUARIO));

        String conteudo = entrada.content() == null ? "" : entrada.content().trim();
        if (conteudo.length() > MAX_CONTEUDO) {
            throw new RegraDeNegocioException("A mensagem é grande demais (máx. " + MAX_CONTEUDO + " caracteres).");
        }

        ChatMessageType tipo = entrada.type() == null ? ChatMessageType.CHAT : entrada.type();

        // JOIN/LEAVE são eventos do sistema: propagam, mas não sujam o histórico.
        if (tipo == ChatMessageType.CHAT || tipo == ChatMessageType.CODE_SNIPPET) {
            chatMessageRepository.saveAndFlush(
                ChatMessageEntity.registrar(sessao, sender, tipo, conteudo)
            );
        }

        return new ChatMessageDTO(sender.getId(), sender.getNome(), conteudo, Instant.now(), tipo);
    }

    /**
     * Histórico da sala: só participantes (autor ou helper) conseguem listar.
     * Devolve em ordem cronológica (das mais antigas para as mais novas).
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDTO> historico(UUID sessionId, UUID userId) {
        SessionEntity sessao = sessionRepository.findById(sessionId)
            .orElseThrow(() -> ResourceNotFoundException.of("Corrida"));

        if (!participa(sessao, userId)) {
            throw new RegraDeNegocioException("Você não participa desta corrida.");
        }

        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
            .stream()
            .map(ChatMessageDTO::from)
            .toList();
    }

    private SessionEntity buscarSalaParticipada(String chatRoomId, UUID userId) {
        UUID id;
        try {
            id = UUID.fromString(chatRoomId);
        } catch (IllegalArgumentException ex) {
            throw new RegraDeNegocioException("Sala de chat inválida.");
        }
        SessionEntity sessao = sessionRepository.findByChatRoomId(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Corrida"));
        if (!participa(sessao, userId)) {
            throw new RegraDeNegocioException("Você não participa desta sala de chat.");
        }
        return sessao;
    }

    private boolean participa(SessionEntity sessao, UUID userId) {
        return sessao.getPost().getAuthor().getId().equals(userId)
            || sessao.getHelper().getId().equals(userId);
    }
}