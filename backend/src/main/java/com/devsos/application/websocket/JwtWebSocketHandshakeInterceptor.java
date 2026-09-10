package com.devsos.application.websocket;

import com.devsos.infrastructure.security.ChatPrincipal;
import com.devsos.infrastructure.security.IdUsuarioLogado;
import com.devsos.infrastructure.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * "A catraca" do WebSocket: VALIDA o JWT no momento em que o cliente pede para
 * CONECTAR. Explica o porquê de existir logo abaixo.
 *
 * <h2>Por que autenticar o WebSocket na mão (e não pelo filtro HTTP)?</h2>
 * O handshake do SockJS até começa como uma requisição HTTP… mas a conexão que
 * fica aberta depois é outra coisa. Depois que o "canal" sobe, cada mensagem
 * STOMP é processada pela infraestrutura de mensageria — que NÃO passa pelo
 * {@code SecurityFilterChain} do REST. Então precisamos de uma camada própria
 * que decida "quem pode entrar": é este interceptor.
 *
 * <p>O JWT vem na URL ({@code ?token=...}) porque clientes WebSocket (navegador,
 * STOMP/JS) não conseguem enviar o cabeçalho {@code Authorization} na conexão.
 * Aceitamos também o cabeçalho (para clientes que conseguem).</p>
 */
@Component
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    /** Chave no mapa de atributos da sessão WebSocket — a "carteirinha" do usuário. */
    public static final String ATTR_CHAT_PRINCIPAL = "CHAT_PRINCIPAL";

    private static final String PARAM_TOKEN = "token";
    private static final String HEADER_AUTH = "Authorization";
    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtWebSocketHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolverToken(request);

        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false; // recusa o handshake: sem token não entra
        }

        try {
            // validarToken também checa assinatura e expiração
            IdUsuarioLogado logado = jwtService.validarToken(token);
            attributes.put(ATTR_CHAT_PRINCIPAL, new ChatPrincipal(logado.id()));
            return true;
        } catch (Exception ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nada a fazer após o handshake neste MVP
    }

    private String resolverToken(ServerHttpRequest request) {
        // 1) preferência: cabeçalho Authorization (clientes que conseguem enviar)
        String header = request.getHeaders().getFirst(HEADER_AUTH);
        if (header != null && header.startsWith(PREFIXO_BEARER)) {
            return header.substring(PREFIXO_BEARER.length());
        }
        // 2) query string ?token=... (o padrão para WebSocket/STOMP no navegador)
        return parsearQueryToken(request.getURI());
    }

    private String parsearQueryToken(URI uri) {
        String query = uri.getRawQuery();
        if (query == null) {
            return null;
        }
        for (String par : query.split("&")) {
            int i = par.indexOf('=');
            if (i > 0 && PARAM_TOKEN.equals(par.substring(0, i))) {
                return par.substring(i + 1);
            }
        }
        return null;
    }
}