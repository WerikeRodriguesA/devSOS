package com.devsos.application.websocket;

import com.devsos.infrastructure.repository.SessionRepository;
import com.devsos.infrastructure.security.ChatPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * O "porteiro" de cada mensagem STOMP: antes de uma mensagem ENTRAR (ir para o
 * broker ou para um {@code @MessageMapping}), este interceptor decide se ela
 * pode passar.
 *
 * <h2>O que ele bloqueia (e por quê)?</h2>
 * <ul>
 *   <li><b>Assinatura de sala</b> ({@code SUBSCRIBE} em {@code /topic/chat/{id}}):
 *       só participante da corrida (autor ou helper) pode "sintonizar" a sala —
 *       mesmo que alguém decore o UUID da sala, não escuta de fora;</li>
 *   <li><b>Envio de mensagem</b> ({@code SEND} de {@code /app/chat/{id}}): mesma
 *       regra — fora da corrida não fala nela.</li>
 * </ul>
 *
 * <h2>Por que na camada de canal e não só no controller?</h2>
 * Porque a assinatura (SUBSCRIBE) nem chega ao controller — ela é evento do
 * protocolo STOMP. Se não barramos aqui, qualquer um assinaria a sala e
 * receberia as mensagens dos outros. É a defesa de "nunca repetir a sala sem
 * conferir o crachá".
 *
 * <p>Quando o bloqueio acontece, o STOMP devolve um frame {@code ERROR} para
 * quem tentou (visível ao cliente).</p>
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final String PREFIXO_TOPICO = "/topic/chat/";
    private static final String PREFIXO_APLICACAO = "/app/chat/";

    private final SessionRepository sessionRepository;

    public ChatChannelInterceptor(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        // CONNECT: o usuário foi autenticado no handshake (JWT) e ficou guardado
        // nos atributos da sessão. Aqui o "despejamos" no header STOMP — é isso
        // que faz o Spring enxergar o principal nas mensagens seguintes.
        if (command == StompCommand.CONNECT) {
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs != null
                    && sessionAttrs.get(JwtWebSocketHandshakeInterceptor.ATTR_CHAT_PRINCIPAL) instanceof ChatPrincipal principal) {
                accessor.setUser(principal);
            }
            return message;
        }

        // SUBSCRIBE e SEND só são restritos quando o destino é uma sala de chat.
        String destination = accessor.getDestination();
        boolean ehDestinoDaSala = destination != null
            && (destination.startsWith(PREFIXO_TOPICO) || destination.startsWith(PREFIXO_APLICACAO));
        if (!ehDestinoDaSala) {
            return message;
        }

        UUID chatRoomId = extrairIdDaSala(destination);
        ChatPrincipal principal = accessor.getUser() instanceof ChatPrincipal cp ? cp : null;
        if (chatRoomId == null || principal == null || !participaDaCorrida(chatRoomId, principal.userId())) {
            throw new MessageDeliveryException("Você não participa desta sala de chat.");
        }
        return message;
    }

    /** A sala pertence a uma corrida onde o usuário é autor OU helper? */
    private boolean participaDaCorrida(UUID chatRoomId, UUID userId) {
        return sessionRepository.findByChatRoomId(chatRoomId)
            .map(s -> s.getPost().getAuthor().getId().equals(userId)
                   || s.getHelper().getId().equals(userId))
            .orElse(false);
    }

    private UUID extrairIdDaSala(String destination) {
        String id;
        if (destination.startsWith(PREFIXO_TOPICO)) {
            id = destination.substring(PREFIXO_TOPICO.length());
        } else {
            id = destination.substring(PREFIXO_APLICACAO.length());
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}