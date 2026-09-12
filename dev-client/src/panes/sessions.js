// panes/sessions.js — Fluxo 5 (aceite já visto no feed) e Fluxo 6:
// listar minhas corridas, detalhar e avançar a máquina de estados
// (MATCHED → ACTIVE → COMPLETED / CANCELLED). Concluir só o helper pode.

import { sessionsApi } from '../api.js';
import { getSession, estaAutenticado } from '../session.js';
import { esc, fmtData, statusChip, objectHtml, buttonHtml } from '../ui.js';

export function init(root, ctx) {
  root.innerHTML = `
    <div class="card">
      <h3>Minhas corridas
        <button id="ses-refresh" class="ghost tiny">recarregar</button>
      </h3>
      <p class="hint">GET /api/sessions — você como autor OU helper (paginado). Para testar os dois papéis, use duas abas/dois usuários.</p>
      <div id="ses-list"></div>
    </div>
  `;

  root.querySelector('#ses-refresh').addEventListener('click', carregar);

  async function carregar() {
    if (!estaAutenticado()) {
      root.querySelector('#ses-list').innerHTML = `<p class="empty">Autentique-se para listar suas corridas.</p>`;
      return;
    }
    const r = await sessionsApi.minhas();
    const list = root.querySelector('#ses-list');
    if (!r.ok || !r.data?.content) {
      list.innerHTML = `<p class="empty">${esc(r.error || 'Sem dados.')}</p>`;
      return;
    }
    const sess = r.data.content;
    if (!sess.length) {
      list.innerHTML = `<p class="empty">Nenhuma corrida sua ainda.</p>`;
      return;
    }
    list.innerHTML = '';
    const usuario = getSession().usuario;
    sess.forEach((s) => list.append(card(s, usuario)));
  }

  function card(s, usuario) {
    const el = document.createElement('div');
    el.className = 'card';
    el.innerHTML = `
      <p style="margin:0 0 4px;font-weight:600">${esc(s.postTitulo)} ${statusChip(s.status)}</p>
      <div class="meta row-dados">
        <span>autor: ${esc(s.authorNome)}</span>
        <span>helper: ${esc(s.helperNome)}</span>
        <span>recompensa: ${s.recompensaValor}</span>
        <span>${fmtData(s.createdAt)}</span>
        ${s.completedAt ? `<span>concluída: ${fmtData(s.completedAt)}</span>` : ''}
      </div>
      <div class="meta mono">sessão ${esc(s.id)} · chatRoom ${esc(s.chatRoomId)}</div>
      <div class="ses-acoes" style="margin-top:8px"></div>
      <div class="ses-detalhe"></div>
    `;

    const acoes = el.querySelector('.ses-acoes');
    const meuId = usuario?.id;
    const souHelper = meuId && s.helperId === meuId;

    acoes.appendChild(buttonHtml('Detalhar (GET)', async () => {
      const d = await sessionsApi.detalhar(s.id);
      el.querySelector('.ses-detalhe').replaceChildren(objectHtml(d.data || d));
      if (!d.ok) ctx.notify(false, d.error);
    }));

    acoes.appendChild(buttonHtml('Abrir chat', () => ctx.events.openChat(s.id, s.chatRoomId)));

    if (s.status === 'MATCHED') {
      acoes.appendChild(buttonHtml('→ ACTIVE', () => transicionar(s.id, 'ACTIVE', acoes)));
      acoes.appendChild(buttonHtml('Cancelar', () => transicionar(s.id, 'CANCELLED', acoes), 'danger'));
    } else if (s.status === 'ACTIVE') {
      acoes.appendChild(buttonHtml('→ COMPLETED (helper)', () => transicionar(s.id, 'COMPLETED', acoes), 'ok', !souHelper));
      acoes.appendChild(buttonHtml('Cancelar', () => transicionar(s.id, 'CANCELLED', acoes), 'danger'));
    } else if (s.status === 'COMPLETED') {
      acoes.appendChild(buttonHtml('Avaliar corrida', () => ctx.events.openReview(s.id)));
    }

    return el;
  }

  async function transicionar(id, status, acoes) {
    const r = await sessionsApi.atualizarStatus(id, status);
    if (r.ok) {
      ctx.notify(true, `Corrida ${status}.`);
      carregar();
    } else {
      ctx.notify(false, r.error);
    }
  }

  return { carregar };
}