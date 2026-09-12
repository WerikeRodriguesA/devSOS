// socket.js — chat em tempo real (WebSocket via SockJS + STOMP).
//
// Espelha o contrato do mobile (mobile/src/services/socket.js): conecta no
// endpoint SockJS /ws-devsos com o JWT na query string, assina
// /topic/chat/{chatRoomId} e publica em /app/chat/{chatRoomId}.
//
// O servidor devolve o quadro CONNECTED com user-name = id do usuário do
// token. Quem não participa da corrida recebe um frame ERROR (e a conexão é
// encerrada) — o erro cai no onError.

import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { getBaseUrl } from './api.js';

/**
 * @param {{token:string, chatRoomId:string, onConnect:Function,
 *          onMessage:Function, onError:Function, debug?:boolean}} opts
 */
export function criarChatSocket({ token, chatRoomId, onConnect, onMessage, onError, debug = false }) {
  let client = null;
  let subscription = null;

  function conectar() {
    const url = `${getBaseUrl()}/ws-devsos?token=${encodeURIComponent(token)}`;

    client = new Client({
      // Transporte SockJS: WebSocket nativo com fallbacks HTTP (browser).
      webSocketFactory: () => new SockJS(url, null, {
        transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
      }),
      connectHeaders: {},
      reconnectDelay: 0,
      debug: (msg) => { if (debug) console.log('[STOMP]', msg); },

      onConnect: () => {
        subscription = client.subscribe(`/topic/chat/${chatRoomId}`, (message) => {
          try {
            if (onMessage) onMessage(JSON.parse(message.body));
          } catch (e) {
            console.warn('[STOMP] mensagem não-parseada:', message.body);
          }
        });
        if (onConnect) onConnect();
      },

      onStompError: (frame) => {
        const msg = (frame && frame.headers && frame.headers.message) || 'Falha no chat.';
        if (onError) onError(msg);
      },

      onWebSocketError: () => {
        if (onError) onError('Sem conexão com o servidor. Verifique sua rede.');
      },
    });

    client.activate();
  }

  function enviar({ content, type = 'CHAT' }) {
    if (!client || !client.connected) return false;
    client.publish({
      destination: `/app/chat/${chatRoomId}`,
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ content, type }),
    });
    return true;
  }

  function desconectar() {
    try {
      if (subscription) { subscription.unsubscribe(); subscription = null; }
      if (client) { client.deactivate(); client = null; }
    } catch (e) {
      console.warn('[STOMP] erro ao desconectar:', e);
    }
  }

  return { conectar, enviar, desconectar };
}