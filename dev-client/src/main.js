// main.js — orquestra o Dev Client Web: cabeçalho (URL + sessão), abas,
// painel de requisições e pontes entre seções.

import { setBaseUrl, getBaseUrl, getAuthToken } from './api.js';
import { carregar as carregarSessao, getSession, estaAutenticado, logout, onSessionChange } from './session.js';
import { maskJwt, esc } from './ui.js';

import * as requestlog from './panes/requestlog.js';
import * as authPane from './panes/auth.js';
import * as feedPane from './panes/feed.js';
import * as sessionsPane from './panes/sessions.js';
import * as chatPane from './panes/chat.js';
import * as reviewsPane from './panes/reviews.js';

const panes = {};

// ------------------------------------------------------------------ utils

function notify(ok, msg) {
  let toast = document.getElementById('toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'toast';
    toast.style.cssText = 'position:fixed;left:14px;bottom:14px;z-index:99;' +
      'background:#1c2438;border:1px solid var(--border);border-radius:8px;' +
      'padding:8px 14px;font-size:13px;max-width:60vw;box-shadow:0 4px 18px rgba(0,0,0,.4)';
    document.body.appendChild(toast);
  }
  toast.style.borderLeft = `4px solid ${ok ? 'var(--ok)' : 'var(--err)'}`;
  toast.textContent = `${ok ? '✓' : '✗'} ${msg}`;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => toast.remove(), ok ? 2600 : 5000);
}

// ------------------------------------------------------------------ abas

function alternarAba(nome) {
  document.querySelectorAll('.nav-btn').forEach((b) =>
    b.classList.toggle('active', b.dataset.tab === nome)
  );
  Object.entries(panes).forEach(([key, pane]) => {
    const el = document.getElementById(`pane-${key}`);
    if (el) el.classList.toggle('active', key === nome);
    else if (key === 'log') { /* painel lateral não tem aba */ }
  });
}

function irAba(nome) {
  alternarAba(nome);
  window.dispatchEvent(new CustomEvent('devsos:aba', { detail: nome }));
}

// ------------------------------------------------------------------ header

function bindHeader() {
  const urlInput = document.getElementById('api-url');
  urlInput.value = getBaseUrl();

  document.getElementById('api-url-apply').addEventListener('click', () => {
    const nova = urlInput.value.trim();
    if (!nova) return;
    const anterior = getBaseUrl();
    setBaseUrl(nova);
    if (anterior !== nova) notify(true, `Base URL → ${getBaseUrl()}`);
  });
  urlInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') document.getElementById('api-url-apply').click();
  });
}

function renderBadge() {
  const el = document.getElementById('session-badge');
  if (!estaAutenticado() || !getSession().token) {
    el.innerHTML = `<span class="chip badge-err">deslogado</span>`;
    return;
  }
  const u = getSession().usuario;
  const nome = u ? u.nome : getAuthToken()?.slice(0, 8) + '…';
  el.innerHTML = `
    <span class="chip badge-ok">logado: ${esc(nome)}</span>
    <span class="chip" title="JWT armazenado">JWT ${maskJwt(getSession().token)}</span>
    <button class="danger tiny" id="header-logout">Sair</button>
  `;
  document.getElementById('header-logout').addEventListener('click', () => {
    logout();
    notify(true, 'Deslogado. JWT removido.');
  });
}

// ------------------------------------------------------------------ eventos entre seções

function bindCrossPane() {
  const ctx = {
    notify,
    events: {
      openChat(sessionId, roomId) {
        irAba('chat');
        panes.chat.abrirSalaCom(sessionId, roomId);
      },
      openReview(sessionId) {
        irAba('reviews');
        panes.reviews.preencherSession(sessionId);
        notify(true, 'sessionId preenchido na aba Avaliações.');
      },
    },
  };

  // Request log: conteúdo próprio (painel lateral)
  requestlog.init(document.querySelector('#app-main aside#request-log-pane'));

  panes.auth = authPane.init(document.getElementById('pane-auth'), ctx);
  panes.feed = feedPane.init(document.getElementById('pane-feed'), ctx);
  panes.sessions = sessionsPane.init(document.getElementById('pane-sessions'), ctx);
  panes.chat = chatPane.init(document.getElementById('pane-chat'), ctx);
  panes.reviews = reviewsPane.init(document.getElementById('pane-reviews'), ctx);

  window.addEventListener('devsos:aba', async (e) => {
    const aba = e.detail;
    if (aba === 'feed' && panes.feed) panes.feed.carregar();
    if (aba === 'sessions' && panes.sessions) panes.sessions.carregar();
    if (aba === 'reviews' && panes.reviews) panes.reviews.carregar();
    if (aba === 'auth' && panes.auth) panes.auth.render();
  });
}

document.querySelectorAll('.nav-btn').forEach((b) =>
  b.addEventListener('click', () => irAba(b.dataset.tab))
);

// ------------------------------------------------------------------ boot

carregarSessao();
bindHeader();
bindCrossPane();
onSessionChange(renderBadge);
renderBadge();
alternarAba('auth');