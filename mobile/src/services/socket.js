/**
 * services/socket.js
 * ---------------------------------------------------------------------------
 * O "canal de comunicação em tempo real" do DevSOS.
 *
 * ANALOGIA (backend):
 * - Conectar ao WebSocket = abrir uma ligação telefônica dedicada entre o
 *   celular e o servidor Spring. Não é um pedido HTTP efêmero (que fecha);
 *   é um canal que fica aberto até o usuário desligar.
 * - Assinar (subscribe) = sintonizar numa frequência de rádio: só recebe
 *   quem está ligado naquele canal (/topic/chat/{id}).
 * - Enviar (send/publish) = falar no microfone. O servidor Spring recebe a
 *   mensagem, valida quem é (via JWT), grava no banco e publica no canal.
 * - No Spring a infraestrutura disso é o SimpMessagingTemplate + Broker.
 *   Aqui, sockjs-client + @stomp/stompjs fazem o papel do cliente STOMP.
 *
 * DEPENDÊNCIAS (por que estas?):
 * - sockjs-client........... implementa o protocolo SockJS. Com
 *   { transports: ['websocket'] } força o WebSocket nativo — necessário no
 *   React Native, onde os transports XHR (xhr-streaming/xhr-polling) não
 *   funcionam nativamente.
 * - @stomp/stompjs (v7)...... implementa os frames STOMP (CONNECT, SUBSCRIBE,
 *   SEND/PUBLISH, DISCONNECT) sobre o WebSocket. É o pacote mantido pelo
 *   projeto Stomp. O pacote legado "stompjs" (v2) resolve o entrypoint de
 *   Node (stomp-node.js, que usa o módulo 'net') e NÃO empacota no Metro do
 *   React Native — por isso usamos a versão moderna.
 *
 * SOBRE O TOKEN:
 * - O backend lê o JWT no handshake via query string (?token=...) ou header
 *   Authorization (ver JwtWebSocketHandshakeInterceptor.java). No SockJS o
 *   token vai na URL base; o sockjs-client propaga os query params para o
 *   transport websocket (urlUtils.addPath preserva o query string).
 * - O ID do usuário é retornado no CONNECTED como `user-name` — nunca o
 *   cliente escolhe esse valor; o servidor define quem é você.
 */

import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { WS_BASE_URL } from '../config';

/**
 * Garante que `self` exista no escopo global.
 * ALGUNS pacotes (ex.: sockjs-client) esperam que `window` ou `self` existam.
 * No React Native (Expo) isso pode não estar definido — aqui colocamos um
 * polyfill mínimo, que o JS moderno ignora em ambientes browser.
 */
if (typeof globalThis.self === 'undefined') {
  globalThis.self = globalThis;
}

const ehDev = typeof __DEV__ !== 'undefined' && __DEV__;

/**
 * Cria um socket STOMP conectado a uma sala de chat.
 *
 * O padrão é aquele "Builder": a função NÃO conecta sozinha — cria um objeto
 * configurado e quem chamou decide quando conectar.
 *
 * Exemplo de uso:
 *   const socket = criarChatSocket({ token, chatRoomId, ... });
 *   socket.conectar();
 *   socket.enviar({ content: 'Oi!', type: 'CHAT' });
 *   socket.desconectar();
 */
export function criarChatSocket({ token, chatRoomId, onConnect, onMessage, onError }) {
  let client = null;
  let subscription = null;

  /**
   * Conecta ao SockJS + STOMP.
   *
   * ANALOGIA (backend): equivale a ligar a infraestrutura de mensageria e
   * aguardar o handshake STOMP (CONNECT -> CONNECTED), como um servidor que
   * "escuta" o broker. A função onConnect é chamada ao concluir o handshake —
   * como um @EventListener(ApplicationReadyEvent).
   */
  function conectar() {
    const url = `${WS_BASE_URL}/ws-devsos?token=${encodeURIComponent(token)}`;

    client = new Client({
      // Fornece o socket "de baixo nível": o SockJS com WebSocket nativo.
      // Em RN os transports XHR não existem nativamente; o SockJS também cuida
      // do fallback e dos protocolos (json/xhr se um dia precisarmos).
      webSocketFactory: () =>
        new SockJS(url, null, { transports: ['websocket'] }),

      // Headers do CONNECT — vazios neste MVP: o JWT já veio na query string
      // do handshake, e a validação é feita lá. Num projeto maior, poderíamos
      // mandar um `Authorization` aqui.
      connectHeaders: {},

      // reconnectDelay = 0 => sem reconexão automática (como no legado).
      reconnectDelay: 0,

      // Silencia os logs verbosos do stomp em produção.
      debug: (msg) => {
        if (ehDev) console.log('[STOMP]', msg);
      },

      // 1) Handshake concluído: agora sim podemos assinar o canal.
      onConnect: () => {
        /**
         * Assina o tópico da sala. subscribe() devolve uma "subscription"
         * que pode ser cancelada depois (como desligar de um canal de rádio).
         */
        subscription = client.subscribe(`/topic/chat/${chatRoomId}`, (message) => {
          try {
            const dados = JSON.parse(message.body);
            if (onMessage) onMessage(dados);
          } catch (e) {
            if (ehDev) console.warn('[STOMP] mensagem não-parseada:', message.body);
          }
        });
        if (onConnect) onConnect();
      },

      // 2) Frame ERROR do servidor (ex.: "Você não participa desta sala de
      //    chat."). Depois dele o Spring fecha a conexão — a UI mostra o erro.
      onStompError: (frame) => {
        const msg = (frame && frame.headers && frame.headers.message) || 'Falha no chat.';
        if (onError) onError(msg);
      },

      // 3) Falha/queda da conexão WebSocket propriamente dita.
      onWebSocketError: () => {
        if (onError) onError('Sem conexão com o servidor. Verifique sua rede.');
      },

      onWebSocketClose: () => {
        if (ehDev) console.log('[STOMP] conexão fechada');
      },
    });

    client.activate();
  }

  /**
   * Envia uma mensagem para a sala.
   *
   * ANALOGIA: equivale a um POST /api/sessions/{id}/messages, mas em tempo
   * real. O destino /app/chat/{id} é interceptado pelo Spring (ChatController),
   * que persiste a mensagem E publica no /topic — todos recebem.
   *
   * O payload é o ChatMessageDTO "parcial": o cliente manda content + type;
   * o servidor preenche senderId, senderNome e timestamp (NUNCA o cliente
   * escolhe a identidade — segurança igual ao @AuthenticationPrincipal).
   */
  function enviar({ content, type = 'CHAT' }) {
    if (!client || !client.connected) return;
    client.publish({
      destination: `/app/chat/${chatRoomId}`,
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ content, type }),
    });
  }

  /**
   * Desconecta: fecha a "ligação" aberta com o servidor.
   *
   * Sempre chame isso quando a tela for desmontada (unmount do componente),
   * senão a conexão fica pendurada. No backend, o equivalente é fechar a
   * sessão; o servidor detecta o corte (onClose) e libera os recursos.
   *
   * ANALOGIA: equivale a um @PreDestroy que libera recursos e threads.
   */
  function desconectar() {
    try {
      if (subscription) {
        subscription.unsubscribe();
        subscription = null;
      }
      if (client) {
        client.deactivate();
        client = null;
      }
    } catch (e) {
      if (ehDev) console.warn('[STOMP] erro ao desconectar:', e);
    }
  }

  return { conectar, enviar, desconectar };
}