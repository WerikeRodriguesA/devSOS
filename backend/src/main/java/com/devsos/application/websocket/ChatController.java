package com.devsos.application.websocket;

import com.devsos.application.dto.chat.ChatMessageDTO;
import com.devsos.application.service.ChatMessageService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Controlador do CHAT EM TEMPO REAL (WebSocket/STOMP).
 *
 * <h2>Por que {@code @Controller} e não {@code @RestController}?</h2>
 * Este não é um endpoint HTTP: não existe "requisição → resposta" aqui. É um
 * OUVIDOR de mensagens STOMP. A função dele é pegar a mensagem que o cliente
 * publicou em {@code /app/chat/{chatRoomId}} e PUBLICAR no tópico da sala:
 *
 * <pre>
 *   Cliente A ──sai de──▶ /app/chat/{sala}   (via SEND)
 *                                │
 *                                ▼
 *                        ChatController (este aqui)
 *                                │
 *                                ▼
 *                       /topic/chat/{sala}   (via broker)
 *                                │
 *                        ▶ Cliente B recebe  (que assinou a sala)
 * </pre>
 *
 * <p>O {@code @RestController} tradicional responde "para a mesma pessoa que
 * pediu" (request → response). Já o WebSocket entrega "para QUEM ESTÁ
 * OUVINDO aquela sala" (publish/subscribe). Quem manda a mensagem e quem a
 * recebe podem ser pessoas diferentes — é o pulo do gato do chat.</p>
 *
 * <h2>Por que {@code SimpMessagingTemplate} e não {@code @SendTo}?</h2>
 * O {@code @SendTo} é fixo/escrivaganha (o destino é uma constante no código).
 * Aqui o destino é o {@code chatRoomId} que vem na URL — coisa de "runtime".
 * O {@code SimpMessagingTemplate.convertAndSend(...)} aceita um destino
 * calculado na hora e ainda nos deixa PERSISTIR a mensagem antes de publicar.
 */
@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageService chatMessageService;

    public ChatController(SimpMessagingTemplate messagingTemplate,
                          ChatMessageService chatMessageService) {
        this.messagingTemplate = messagingTemplate;
        this.chatMessageService = chatMessageService;
    }

    /**
     * Captura mensagens enviadas a {@code /app/chat/{chatRoomId}}.
     *
     * @param chatRoomId a sala (vem da URL do destino STOMP)
     * @param entrada    o que o cliente digitou (content + type; sender e hora
     *                   o servidor define)
     * @param principal  o usuário logado na conexão (definido no handshake JWT
     *                   e propagado pelo {@code ChatChannelInterceptor})
     */
    @MessageMapping("/chat/{chatRoomId}")
    public void enviarMensagem(@DestinationVariable UUID chatRoomId,
                               ChatMessageDTO entrada,
                               Principal principal) {
        UUID senderId = principal == null ? null : UUID.fromString(principal.getName());

        ChatMessageDTO saida = chatMessageService.registrar(chatRoomId.toString(), senderId, entrada);

        // Publica para QUEM ASSINOU a sala (broker /topic/chat/{chatRoomId})
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId, saida);
    }
}