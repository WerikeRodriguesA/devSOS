/**
 * hooks/useChatMessages.js
 * ---------------------------------------------------------------------------
 * Hook que encapsula TODO o ciclo de vida do chat:
 *   1. Carrega o histórico via REST (mensagens que JÁ existem).
 *   2. Abre o WebSocket e assina o tópico da sala (mensagens NOVAS).
 *   3. Expõe `enviar` para mandar mensagens pelo STOMP.
 *
 * O QUE É UM "HOOK"?
 * É uma função especial que React fornece ("use...") capaz de LEMBRAR estado
 * entre renderizações e reagir ao ciclo de vida do componente.
 *
 * ANALOGIA (backend):
 * - useState  = um atributo da entidade + um gatilho de "re-render" automático.
 * - useEffect = um ouvinte de eventos / @PostConstruct: roda quando o
 *   componente "nasce" e limpa (return) quando ele "morre".
 * - O `return` do useEffect é o equivalente ao finally: executa a limpeza
 *   (fecha o socket) sempre, mesmo se der erro — evita vazamento de conexão.
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { criarChatSocket } from '../services/socket';
import { extrairErro, sessionsApi } from '../services/api';

/**
 * @param {Object} opcoes
 * @param {string} opcoes.sessionId   id da corrida (para carregar histórico)
 * @param {string} opcoes.chatRoomId  id da sala (para assinar o tópico)
 * @param {string} opcoes.token       JWT para o handshake do WebSocket
 */
export function useChatMessages({ sessionId, chatRoomId, token }) {
  // Estado da tela de chat
  const [mensagens, setMensagens] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [conectado, setConectado] = useState(false);
  const [erro, setErro] = useState(null);

  // Referência polivalente: NÃO dispara re-render quando mudamos o valor.
  // É o equivalente a um campo mutável da classe (sem ser "observado").
  // Usamos para guardar o socket e conseguir enviar mensagens de qualquer
  // ponto sem recriar o objeto a cada renderização.
  const socketRef = useRef(null);

  useEffect(() => {
    // "Sinal de vida": durante UMA montagem do componente, só aplicamos
    // setState se este booleano ainda for true. Evita setState após unmount.
    let ativo = true;

    // ------------------------------------------------------------------
    // 1) Histórico via REST (mensagens persistidas no banco)
    // ------------------------------------------------------------------
    (async () => {
      try {
        setCarregando(true);
        const historico = await sessionsApi.historico(sessionId);
        if (ativo) setMensagens(historico);
      } catch (e) {
        if (ativo) setErro(extrairErro(e));
      } finally {
        if (ativo) setCarregando(false);
      }
    })();

    // ------------------------------------------------------------------
    // 2) WebSocket: abre o canal e assina o tópico da sala
    // ------------------------------------------------------------------
    const socket = criarChatSocket({
      token,
      chatRoomId,
      onConnect: () => {
        // "Linha conectada" — a tela pode avisar no balão de status.
        if (ativo) setConectado(true);
      },
      onMessage: (novaMensagem) => {
        if (!ativo) return;
        // Adiciona evitando duplicatas (o chat pode receber o eco do próprio
        // envio + histórico que ainda não terminamos de carregar).
        setMensagens((anteriores) => {
          const jaExiste = anteriores.some(
            (m) =>
              m.senderId === novaMensagem.senderId &&
              m.timestamp === novaMensagem.timestamp &&
              m.content === novaMensagem.content
          );
          if (jaExiste) return anteriores;
          return [...anteriores, novaMensagem];
        });
      },
      onError: (mensagemErro) => {
        // Erros como "Você não participa desta sala de chat." caem aqui.
        if (ativo) setErro(mensagemErro);
      },
    });

    socketRef.current = socket;
    socket.conectar();

    // ------------------------------------------------------------------
    // 3) Cleanup no unmount — o "finally" da montagem
    // ------------------------------------------------------------------
    return () => {
      ativo = false;
      socket.desconectar();
      socketRef.current = null;
    };
  }, [sessionId, chatRoomId, token]);

  /**
   * Enviar nova mensagem: chama o STOMP SEND /app/chat/{chatRoomId}.
   * O servidor preenche senderId/senderNome/timestamp e república no tópico.
   *
   * type pode ser 'CHAT' (texto) ou 'CODE_SNIPPET' (trecho de código).
   */
  const enviar = useCallback((content, type = 'CHAT') => {
    socketRef.current?.enviar({ content, type });
  }, []);

  return { mensagens, carregando, conectado, erro, enviar };
}