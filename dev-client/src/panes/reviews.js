// panes/reviews.js — avaliações mútuas (pós-corrida concluída):
// listar as que EU recebi + avaliar uma corrida COMPLETED (o avaliado é
// sempre o outro lado da corrida, decidido pelo backend).

import { reviewsApi, sessionsApi } from '../api.js';
import { estaAutenticado } from '../session.js';
import { esc, fmtData, objectHtml, buttonHtml } from '../ui.js';

export function init(root, ctx) {
  root.innerHTML = `
    <div class="card">
      <h3>Avaliar corrida concluída
        <button id="rev-refresh-sessions" class="ghost tiny">trazer corridas concluídas</button>
      </h3>
      <div class="bloco">
        <span class="grupo"><label>sessionId (COMPLETED)</label><input id="rev-session" spellcheck="false" placeholder="UUID" /></span>
        <span class="grupo"><label>Nota (1–5)</label>
          <select id="rev-nota"><option>1</option><option>2</option><option>3</option><option>4</option><option selected>5</option></select></span>
        <span class="grupo"><label>Comentário (opcional)</label><input id="rev-comentario" /></span>
        <span class="grupo"><label>&nbsp;</label><button id="rev-avaliar">Avaliar</button></span>
      </div>
      <p class="hint">POST /api/reviews — exige corrida COMPLETED e que você participe; 1 review por pessoa por corrida.</p>
      <div id="rev-done"></div>
    </div>

    <div class="card">
      <h3>Avaliações que EU recebi
        <button id="rev-refresh" class="ghost tiny">recarregar</button>
      </h3>
      <div id="rev-list"></div>
    </div>
  `;

  root.querySelector('#rev-avaliar').addEventListener('click', avaliar);
  root.querySelector('#rev-refresh').addEventListener('click', listar);
  root.querySelector('#rev-refresh-sessions').addEventListener('click', trazerConcluidas);

  async function avaliar() {
    if (!estaAutenticado()) { ctx.notify(false, 'Autentique-se primeiro.'); return; }
    const sessionId = root.querySelector('#rev-session').value.trim();
    const nota = Number(root.querySelector('#rev-nota').value);
    const comentario = root.querySelector('#rev-comentario').value.trim();
    if (!sessionId) { ctx.notify(false, 'Informe o sessionId.'); return; }
    const r = await reviewsApi.avaliar({ sessionId, nota, comentario });
    const box = root.querySelector('#rev-done');
    box.innerHTML = '';
    if (r.ok) {
      box.append(objectHtml(r.data));
      ctx.notify(true, 'Avaliação registrada.');
      listar();
    } else {
      box.innerHTML = `<p class="meta" style="color:var(--err)">${esc(r.error)}</p>`;
      ctx.notify(false, r.error);
    }
  }

  async function listar() {
    if (!estaAutenticado()) {
      root.querySelector('#rev-list').innerHTML = `<p class="empty">Autentique-se para ver suas avaliações.</p>`;
      return;
    }
    const r = await reviewsApi.recebidas();
    const list = root.querySelector('#rev-list');
    if (!r.ok || !r.data?.content) {
      list.innerHTML = `<p class="empty">${esc(r.error || 'Sem dados.')}</p>`;
      return;
    }
    const itens = r.data.content;
    if (!itens.length) { list.innerHTML = `<p class="empty">Ninguém avaliou você ainda.</p>`; return; }
    list.innerHTML = '';
    itens.forEach((rv) => {
      const card = document.createElement('div');
      card.className = 'card';
      card.innerHTML = `
        <div class="row-dados">
          <span>nota: <b>${rv.nota}/5</b></span>
          <span>avaliador: ${esc(rv.reviewerNome)}</span>
          <span>sua média agora: ${rv.mediaAvaliacoesDoAvaliado}</span>
          <span>${fmtData(rv.createdAt)}</span>
        </div>
        ${rv.comentario ? `<div>${esc(rv.comentario)}</div>` : ''}
        <div class="meta mono">request ${esc(rv.id)} · sessão ${esc(rv.sessionId)}</div>
      `;
      list.append(card);
    });
  }

  async function trazerConcluidas() {
    const r = await sessionsApi.minhas();
    const done = r.ok ? (r.data?.content || []).filter((s) => s.status === 'COMPLETED') : [];
    const box = root.querySelector('#rev-done');
    box.innerHTML = '';
    if (!r.ok) { box.innerHTML = `<p class="meta" style="color:var(--err)">${esc(r.error)}</p>`; return; }
    if (!done.length) { box.innerHTML = `<p class="empty">Nenhuma corrida sua COMPLETED.</p>`; return; }
    done.forEach((s) => {
      const row = document.createElement('div');
      row.style.marginBottom = '8px';
      row.append(
        document.createTextNode(`${s.postTitulo} (${s.status}) — `),
        buttonHtml('usar esta corrida', () => {
          root.querySelector('#rev-session').value = s.id;
          root.querySelector('#rev-session').scrollIntoView({ block: 'nearest' });
        })
      );
      box.append(row);
    });
  }

  return { carregar: listar, preencherSession: (id) => { root.querySelector('#rev-session').value = id; } };
}