package com.devsos.application.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuração do WebSocket + STOMP do DevSOS.
 *
 * <h2>As três peças que a gente monta aqui</h2>
 * <ol>
 *   <li><b>Endpoint de conexão ({@code /ws-devsos})</b> — a "porta de entrada"
 *       da ligação. O cliente conecta aqui (via SockJS) e a partir daí o canal
 *       fica aberto.</li>
 *   <li><b>Broker simples ({@code /topic})</b> — o "central de rádio": publica
 *       as mensagens nos canais e quem está sintonizado recebe.</li>
 *   <li><b>Prefixo de aplicação ({@code /app})</b> — a porta por onde o CLIENTE
 *       ENVIA mensagens para os {@code @MessageMapping}.</li>
 * </ol>
 *
 * <h2>Regra de ouro (memorize)</h2>
 * <ul>
 *   <li>Destino começando com <b>{@code /app}</b> → vai para o CONTROLLER
 *       (Java), que processa e publica;</li>
 *   <li>Destino começando com <b>{@code /topic}</b> → vai para o BROKER
 *       (francês "assinar/palco"), e espalha a quem está inscrito.</li>
 * </ul>
 *
 * <h2>O que é CORS no contexto de WebSockets?</h2>
 * É a mesma regra de "de qual endereço posso falar com você". O REST usa
 * {@code Access-Control-Allow-Origin}; aqui o navegador também checa a origem
 * da conexão antes de deixar abrir. Com {@code setAllowedOriginPatterns("*")}
 * liberamos QUALQUER origem em desenvolvimento — em produção você restringiria
 * para o domínio do front (ex.: {@code https://app.devsos.com}).
 *
 * <h2>Por que SockJS?</h2>
 * Nem todo cliente (rede corporativa, proxy) suporta WebSocket nativo. O
 * SockJS oferece "camadas de transporte" (WebSocket, HTTP) e faz fallback
 * automático — o app continua funcionando mesmo onde WebSocket é bloqueado.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtWebSocketHandshakeInterceptor jwtHandshakeInterceptor;
    private final ChatChannelInterceptor chatChannelInterceptor;

    public WebSocketConfig(JwtWebSocketHandshakeInterceptor jwtHandshakeInterceptor,
                           ChatChannelInterceptor chatChannelInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.chatChannelInterceptor = chatChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-devsos")
            .setAllowedOriginPatterns("*")   // dev: aceita origem de qualquer front
            .addInterceptors(jwtHandshakeInterceptor) // autentica a conexão (JWT)
            .withSockJS();                   // fallback para quem não tem WebSocket
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Broker simples: canal onde MENSAGENS SAEM para os inscritos
        registry.enableSimpleBroker("/topic");
        // Prefixo de onde as mensagens ENTRAM (destinados aos controllers)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // "Porteiro" de TODA mensagem que chega do cliente (antes do broker/controller)
        registration.interceptors(chatChannelInterceptor);
    }
}