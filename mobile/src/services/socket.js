/**
 * services/socket.js
 * ---------------------------------------------------------------------------
 * O "canal de comunicação em tempo real" do DevSOS.
 *
 * ANALOGIA (backend):
 * - Conectar ao WebSocket = abrir uma ligação telefônica dedicada entre o
 *   celular e o servidor Spring. Não é um pedido HTTP efêmero (que fecha);
 *   é um FILHO que fica aberto até o usuário desligar.
 * - Assinar (subscribe) = sintonizar numa frequência de rádio: só recebe quem
 *   está "ligado" naquele canal (/topic/chat/{id}).
 * - Enviar (send) = falar no microfone. O servidor (Spring) recebe a mensagem,
 *   valida quem é (via JWT), grava no banco e publica no canal para todos.
 * - No Spring a infraestrutura disso é o SimpMessagingTemplate + Broker.
 *   Aqui, o sockjs-client + stompjs fazem o papel do cliente STOMP.
 *
 * DEPENDÊNCIAS:
 * - sockjs-client: implementa o protocolo SockJS (fallback automático de
 *   transports; com { transports: ['websocket'] } força o WebSocket nativo).
 * - stompjs (legado, v2): gerencia frames STOMP sobre SockJS (CONNECT,
 *   SUBSCRIBE, SEND, DISCONNECT).
 * - Para um novo projeto, @stomp/stompjs (v5+) é recomendado. O uso aqui
 *   honra o pedido explícito do solicitante.
 *
 * SOBRE O TOKEN:
 * - O backend lê o JWT no handshake via query string (?token=...) ou header
 *   Authorization (ver JwtWebSocketHandshakeInterceptor.java). No SockJS,
 *   o token é colocado na URL base; o sockjs-client propagado aos transports
 *   preserva query params.
 * - O ID do usuário é retornado no CONNECTED como `user-name` (nunca o
 *   cliente escolhe esse valor — o servidor define quem é você).
 */

import SockJS from 'sockjs-client';
import Stomp from 'stompjs';
import { WS_BASE_URL } from '../config';

/**
 * Garante que `self` exista no escopo global.
 * ALGUNS pacotes legados (sockjs-client) esperam que `window` ou `self` existam.
 * No React Native (Expo) isso pode não estar definido — aqui colocamos um
 * polyfill mínimo que o JS moderno ignora em ambientes browser.
 */
if (typeof globalThis.self === 'undefined') {
  globalThis.self = globalThis;
}

/**
 * Cria um socket STOMP conectado a uma sala de chat.
 *
 * O padrão aqui é aquele "Builder": a função não conecta sozinha — cria um
 * objeto configurado e quem chamou decide quando conectar.
 *
 * Exemplo de uso:
 *   const socket = criarChatSocket({ token, chatRoomId, ... });
 *   socket.conectar();
 *   socket.enviar({ content: 'Oi!', type: 'CHAT' });
 *   socket.desconectar();
 */
export function criarChatSocket({ token, chatRoomId, onConnect, onMessage, onError }) {
  let stompClient = null;
  let sock = null;

  /**
   * Conecta ao SockJS + STOMP.
   *
   * ANALOGIA (backend): equivale a chamar a infraestrutura do
   * que começa a escutar o broker. A função onConnect é chamada quando o
   * handshaking STOMP (CONNECT/CONNECTED) é concluído — como um
   * @EventListener(ApplicationReadyEvent).
   */
  function conectar() {
    const url = `${WS_BASE_URL}/ws-devsos?token=${encodeURIComponent(token)}`;

    /**
     * Forçamos o transport 'websocket' porque no React Native os transports
     * baseados em XHR (xhr-streaming, xhr-polling) não funcionam nativamente.
     * WebSocket nativo é suportado pelo RN e pelo Expo.
     */
    sock = new SockJS(url, null, { transports: ['websocket'] });
    stompClient = Stomp.over(sock);

    // Silencia logs verbosos do stompjs legado em produção.
    stompClient.debug = (msg) => {
      if (__DEV__) console.log('[STOMP]', msg);
    };

    /**
     * Headers do CONNECT — o backend aceita vazio neste MVP (o JWT já veio
     * na query string do handshake, e a validação é feita lá).
     * Num projeto maior, poderia enviar um `Authorization` aqui.
     */
    stompClient.connect(
      {},
      // Sucesso: canal aberto — devolve a "confirmação" de conexão
      (frame) => {
        if (onConnect) onConnect(frame);
      },
      // Erro de conexão (chamada quando o handshake falha ou a conexão cai)
      (erro) => {
        const msg = erro && erro.headers && erro.headers.message
          ? erro.headers.message
          : 'Conexão com o chat encerrada.';
        if (onError) onError(msg);
      }
    );

    /**
     * Assina o tópico da sala. subscribe() devolve um "subscription" que pode
     * ser cancelado depois (como desligar de um canal de rádio).
     *
     * IMPORTANTE: o subscribe DEVE ser feito DEPOIS do connect estar ativo.
     * Se fizer antes, o stompClient devolve erro de "still connecting".
     */
    stompClient.subscribe(`/topic/chat/${chatRoomId}`, (message) => {
      try {
        const dados = JSON.parse(message.body);
        if (onMessage) onMessage(dados);
      } catch (e) {
        if (__DEV__) console.warn('[STOMP] mensagem não-parseada:', message.body);
      }
    });
  }

  /**
   * Envia uma mensagem para a sala.
   *
   * ANALOGIA: equivale a um POST /api/sessions/{id}/messages, mas em tempo real.
   * O destino /app/chat/{id} é interceptado pelo Spring (ChatController),
   * que persiste a mensagem E publica no /topic — todos recebem.
   *
   * O payload é o ChatMessageDTO "parcial": o cliente manda content + type;
   * o servidor preenche senderId, senderNome e timestamp (NUNCA o cliente
   * escolhe a identidade — segurança igual ao @AuthenticationPrincipal do
   * backend).
   */
  function enviar({ content, type = 'CHAT' }) {
    if (!stompClient || !stompClient.connected) return;
    stompClient.send(
      `/app/chat/${chatRoomId}`,
      { 'content-type': 'application/json' },
      JSON.stringify({ content, type })
    );
  }

  /**
   * Desconecta: fecha a "ligação" aberta com o servidor.
   *
   * Sempre chame isso quando a tela for desmontada (unmount do componente),
   * senão a conexão fica pendurada em memória. No backend, o equivalente é
   * fechar um SseEmitter ou chamar messagingTemplate conectado; o servidor
   * detecta o corte (onClose) e libera a sessão.
   *
   * ANALOGIA: equivale a um @PreDestroy que libera recursos (conexão JDBC, threads, etc.).
   */
  function desconectar() {
    try {
      if (stompClient && stompClient.connected) {
        stompClient.disconnect(() => {});
      }
      if (sock && sock.close) {
        sock.close();
      }
    } catch (e) {
      if (__DEV__) console.warn('[STOMP] erro ao desconectar:', e);
    }
  }

  return { conectar, enviar, desconectar };
}