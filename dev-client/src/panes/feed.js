// panes/feed.js — Fluxo 3 (feed) e Fluxo 4 (criar pedido) + Fluxo 5
// (aceitar socorro) acionado a partir do card do post.

import { postsApi, sessionsApi } from '../api.js';
import { estaAutenticado } from '../session.js';
import { esc, fmtData, statusChip, objectHtml, buttonHtml } from '../ui.js';

export function init(root, ctx) {
  root.innerHTML = `
    <div class="card">
      <h3>Criar publicação (pedido de ajuda)</h3>
      <div class="bloco">
        <span class="grupo"><label>Título (3–160)</label><input id="feed-titulo" /></span>
        <span class="grupo"><label>Tipo</label>
          <select id="feed-tipo"><option value="FREE">FREE</option><option value="PAID">PAID</option></select>
        </span>
        <span class="grupo"><label>Recompensa (FREE ⇒ 0)</label><input id="feed-recompensa" type="number" value="0" min="0" step="0.01" /></span>
        <span class="grupo"><label>Tags (vírgula)</label><input id="feed-tags" placeholder="java, spring" /></span>
      </div>
      <div class="bloco">
        <span class="grupo" style="flex:1"><label>Descrição (10–5000)</label>
          <textarea id="feed-descricao" rows="3"></textarea></span>
      </div>
      <div class="bloco">
        <span class="grupo" style="flex:1"><label>mediaUrl (opcional)</label><input id="feed-mediaurl" placeholder="https://i.imgur.com/x.png" /></span>
      </div>
      <button id="feed-criar-btn">Publicar</button>
      <span class="hint" style="margin-left:10px">POST /api/posts — exige JWT (o autor vem do token).</span>
    </div>

    <div class="card">
      <h3>Feed <button id="feed-refresh" class="ghost tiny">recarregar</button></h3>
      <div id="feed-list"></div>
    </div>
  `;

  root.querySelector('#feed-criar-btn').addEventListener('click', async () => {
    if (!estaAutenticado()) { ctx.notify(false, 'Faça login antes de publicar.'); return; }
    const tags = root.querySelector('#feed-tags').value.split(',').map((t) => t.trim()).filter(Boolean);
    const tipo = root.querySelector('#feed-tipo').value;
    const recompensaValor = Number(root.querySelector('#feed-recompensa').value || 0);
    const payload = {
      titulo: root.querySelector('#feed-titulo').value.trim(),
      descricao: root.querySelector('#feed-descricao').value.trim(),
      mediaUrl: root.querySelector('#feed-mediaurl').value.trim(),
      tags,
      tipo,
      recompensaValor,
    };
    const r = await postsApi.criar(payload);
    ctx.notify(r.ok, r.ok ? 'Post criado (201).' : r.error);
    if (r.ok) carregar();
  });

  root.querySelector('#feed-refresh').addEventListener('click', carregar);

  async function carregar() {
    const r = await postsApi.feed();
    const list = root.querySelector('#feed-list');
    if (!r.ok || !r.data?.content) {
      list.innerHTML = `<p class="empty">${esc(r.error || 'Sem dados.')}</p>`;
      return;
    }
    const posts = r.data.content;
    if (!posts.length) {
      list.innerHTML = `<p class="empty">Nenhum post OPEN no feed.</p>`;
      return;
    }
    list.innerHTML = posts.map((p) => `
      <div class="card post-card">
        <p class="titulo">${esc(p.titulo)} ${statusChip(p.status)}</p>
        <div class="meta row-dados">
          <span>autor: ${esc(p.autorNome)}</span>
          <span>tipo: ${esc(p.tipo)}</span>
          <span>recompensa: ${p.recompensaValor}</span>
          <span>${fmtData(p.createdAt)}</span>
          ${p.autorAvatarUrl ? `<span>avatar: ${esc(p.autorAvatarUrl)}</span>` : ''}
        </div>
        ${p.mediaUrl ? `<p class="meta">figura: ${esc(p.mediaUrl)}</p>` : ''}
        <div class="desc">${esc(p.descricao)}</div>
        <div class="meta row-dados">tags: ${(p.tags || []).map(esc).join(', ') || '—'}</div>
        <button class="info feed-aceitar" data-post='${esc(JSON.stringify(p))}'>Aceitar socorro</button>
        <span class="hint">POST /api/sessions { postId }</span>
        <div class="feed-detalhe"></div>
      </div>
    `).join('');

    list.querySelectorAll('.feed-aceitar').forEach((btn) => {
      btn.addEventListener('click', () => aceitarSocorro(btn));
    });
  }

  async function aceitarSocorro(btn) {
    if (!estaAutenticado()) { ctx.notify(false, 'Faça login antes de aceitar um socorro.'); return; }
    const post = JSON.parse(btn.dataset.post);
    const r = await sessionsApi.aceitar(post.id);
    const det = btn.parentElement.querySelector('.feed-detalhe');
    if (r.ok) {
      const row = document.createElement('div');
      row.innerHTML = `<div style="margin-top:8px"></div>`;
      row.querySelector('div').append(
        objectHtml(r.data),
        buttonHtml('Abrir chat', () => ctx.events.openChat(r.data.id, r.data.chatRoomId))
      );
      det.replaceChildren(row);
    } else {
      det.innerHTML = `<p class="meta" style="color:var(--err)">${esc(r.error)}</p>`;
    }
    ctx.notify(r.ok, r.ok ? 'Socorro aceito. Sessão criada.' : r.error);
    if (r.ok) { btn.disabled = true; btn.textContent = 'Aceito ✓'; }
  }

  return { carregar };
}