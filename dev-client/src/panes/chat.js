// panes/chat.js — Fluxo 7 (chat): histórico REST + tempo real via
// WebSocket (SockJS/STOMP). Abre uma sala a partir das corridas/feed.

import { sessionsApi } from '../api.js';
import { tokenAtual, estaAutenticado, getSession } from '../session.js';
import { criarChatSocket } from '../socket.js';
import { esc, fmtData } from '../ui.js';

export function init(root, ctx) {
  let socket = null;
  let chatRoomId = null;

  root.innerHTML = `
    <div class="card">
      <h3>Chat da corrida</h3>
      <div class="bloco">
        <span class="grupo"><label>sessionId (corrida)</label><input id="chat-session" placeholder="UUID" spellcheck="false" /></span>
        <span class="grupo"><label>chatRoomId (sala)</label><input id="chat-room" placeholder="UUID" spellcheck="false" /></span>
        <span class="grupo"><label>&nbsp;</label>
          <button id="chat-open" class="info">Abrir sala</button></span>
      </div>
      <p class="hint">Fluxo: GET /api/sessions/{id}/messages (histórico) → conectar WebSocket →
        assinar /topic/chat/{chatRoomId} → enviar em /app/chat/{chatRoomId}.</p>
      <div id="chat-status"></div>
      <div id="chat-box"></div>
      <div class="bloco">
        <span class="grupo"><label>Mensagem</label>
          <textarea id="chat-input" rows="2" style="min-width:320px"></textarea></span>
        <span class="grupo"><label>Tipo</label>
          <select id="chat-type"><option value="CHAT">CHAT</option><option value="CODE_SNIPPET">CODE_SNIPPET</option></select></span>
        <span class="grupo"><label>&nbsp;</label><button id="chat-send">Enviar</button></span>
      </div>
      <div id="chat-events"></div>
    </div>
  `;

  root.querySelector('#chat-open').addEventListener('click', abrirSala);
  root.querySelector('#chat-send').addEventListener('click', enviarMensagem);
  root.querySelector('#chat-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) enviarMensagem();
  });

  function statusLock(texto, classe = '') {
    const box = root.querySelector('#chat-status');
    box.innerHTML = `<span class="chip ${classe}">${esc(texto)}</span>`;
  }

  async function abrirSala() {
    const sessionId = root.querySelector('#chat-session').value.trim();
    chatRoomId = root.querySelector('#chat-room').value.trim();
    if (!sessionId) { ctx.notify(false, 'Informe o sessionId da corrida.'); return; }
    if (!chatRoomId) { ctx.notify(false, 'Informe o chatRoomId (aparece na corrida / aceite).'); return; }
    if (!estaAutenticado()) { ctx.notify(false, 'Faça login para abrir o chat.'); return; }

    fecharSocket();
    root.querySelector('#chat-box').innerHTML = '';

    // 1) Histórico via REST (mensagens persistidas)
    statusLock('carregando histórico…', 'badge-info');
    const h = await sessionsApi.historico(sessionId);
    if (!h.ok) {
      statusLock(h.error, 'badge-err');
      return;
    }
    (h.data || []).forEach((m) => adicionarMensagem(m));
    statusLock(`${(h.data || []).length} mensagem(ns) no histórico`, 'badge-info');

    // 2) WebSocket / STOMP em tempo real
    socket = criarChatSocket({
      token: tokenAtual(),
      chatRoomId,
      onConnect: () => statusLock('WebSocket CONECTADO', 'badge-ok'),
      onMessage: (m) => { adicionarMensagem(m); logEvento('recebido (tempo real)'); },
      onError: (msg) => {
        statusLock(msg, 'badge-err');
        logEvento(`erro STOMP: ${msg}`);
      },
    });
    socket.conectar();
  }

  function adicionarMensagem(m) {
    const box = root.querySelector('#chat-box');
    const meuId = getSession()?.usuario?.id;
    const sel = meuId && m.senderId === meuId;
    const msg = document.createElement('div');
    msg.className = `msg${sel ? ' sel' : ''}`;
    msg.innerHTML = `
      <div class="rem">${esc(m.senderNome || m.senderId)} · ${fmtData(m.timestamp)} · ${esc(m.type)}</div>
      <div class="bal">${m.type === 'CODE_SNIPPET' ? `<code>${esc(m.content)}</code>` : esc(m.content)}</div>
    `;
    box.append(msg);
    box.scrollTop = box.scrollHeight;
  }

  function enviarMensagem() {
    const content = root.querySelector('#chat-input').value;
    const type = root.querySelector('#chat-type').value;
    if (!content.trim()) return;
    if (!socket) { ctx.notify(false, 'Abra uma sala primeiro.'); return; }
    const enviado = socket.enviar({ content, type });
    if (enviado) {
      root.querySelector('#chat-input').value = '';
      logEvento(`enviado → /app/chat/${chatRoomId}`);
    } else {
      ctx.notify(false, 'Socket não está conectado.');
    }
  }

  function logEvento(txt) {
    const box = root.querySelector('#chat-events');
    const line = document.createElement('div');
    line.className = 'meta';
    line.textContent = `[${fmtData(new Date().toISOString())}] ${txt}`;
    box.append(line);
  }

  function fecharSocket() {
    if (socket) { socket.desconectar(); socket = null; }
  }

  function carregarSession(id, sala) {
    root.querySelector('#chat-session').value = id;
    root.querySelector('#chat-room').value = sala;
  }

  return {
    abrirSalaCom: (id, sala) => { carregarSession(id, sala); abrirSala(); },
    preencherSalaCom: carregarSession,
    abrirSala,
  };
}