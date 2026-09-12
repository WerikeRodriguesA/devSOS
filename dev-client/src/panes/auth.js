// panes/auth.js — Fluxo 1 (cadastro) e Fluxo 2 (login/logout) +
// visualização do usuário autenticado e perfil.

import { login, logout, registrar, getSession, estaAutenticado } from '../session.js';
import { usersApi } from '../api.js';
import { esc, fmtData, maskJwt } from '../ui.js';

export function init(root, ctx) {
  root.innerHTML = `
    <div class="card">
      <h3>Login</h3>
      <div class="bloco">
        <span class="grupo"><label>E-mail</label><input id="auth-login-email" type="email" value="" /></span>
        <span class="grupo"><label>Senha</label><input id="auth-login-senha" type="password" value="" /></span>
        <span class="grupo"><label>&nbsp;</label><button id="auth-login-btn">Entrar</button></span>
      </div>
      <p class="hint">POST /api/auth/login — resposta: token + usuário. O token passa a ser enviado em <code>Authorization: Bearer</code>.</p>
    </div>

    <div class="card">
      <h3>Cadastro</h3>
      <div class="bloco">
        <span class="grupo"><label>Nome</label><input id="auth-reg-nome" /></span>
        <span class="grupo"><label>E-mail</label><input id="auth-reg-email" type="email" /></span>
        <span class="grupo"><label>Senha (mín. 8)</label><input id="auth-reg-senha" type="password" /></span>
        <span class="grupo"><label>GitHub (opcional)</label><input id="auth-reg-github" /></span>
        <span class="grupo"><label>&nbsp;</label><button id="auth-reg-btn">Criar conta</button></span>
      </div>
      <p class="hint">POST /api/auth/register — o JWT já vem na resposta (201), então o cadastro já autentica.</p>
    </div>

    <div class="card">
      <h3>Sessão atual</h3>
      <div id="auth-session-box"></div>
      <div class="bloco" style="margin-top:10px">
        <button id="auth-perfil-btn" class="ghost">Buscar perfil (GET /api/users/:id)</button>
        <button id="auth-logout-btn" class="danger">Logout</button>
      </div>
    </div>
  `;

  root.querySelector('#auth-login-btn').addEventListener('click', async () => {
    const email = root.querySelector('#auth-login-email').value.trim();
    const senha = root.querySelector('#auth-login-senha').value;
    const r = await login(email, senha);
    ctx.notify(r.ok, r.ok ? 'Login OK — API autenticada.' : r.error);
  });

  root.querySelector('#auth-reg-btn').addEventListener('click', async () => {
    const nome = root.querySelector('#auth-reg-nome').value.trim();
    const email = root.querySelector('#auth-reg-email').value.trim();
    const senha = root.querySelector('#auth-reg-senha').value;
    const githubUsername = root.querySelector('#auth-reg-github').value.trim();
    const r = await registrar({ nome, email, senha, githubUsername });
    ctx.notify(r.ok, r.ok ? 'Cadastro OK — já autenticado.' : r.error);
  });

  root.querySelector('#auth-logout-btn').addEventListener('click', () => {
    logout();
    ctx.notify(true, 'Logout local. O JWT deixou de ser enviado.');
  });

  root.querySelector('#auth-perfil-btn').addEventListener('click', async () => {
    const u = getSession().usuario;
    if (!u) { ctx.notify(false, 'Sem sessão. Faça login primeiro.'); return; }
    const r = await usersApi.perfil(u.id);
    renderSessao();
    if (!r.ok) ctx.notify(false, r.error);
  });

  function renderSessao() {
    const box = root.querySelector('#auth-session-box');
    if (!estaAutenticado() || !getSession().token) {
      box.innerHTML = `<p class="empty">Deslogado — nenhum JWT em memória.</p>`;
      return;
    }
    const u = getSession().usuario;
    if (!u) { box.innerHTML = `<p class="empty">Token presente, mas usuário não carregado.</p>`; return; }
    box.innerHTML = `
      <div class="row-dados">
        <span class="chip badge-ok">autenticado</span>
        <span>${esc(u.nome)} <span class="mono">(${esc(u.id)})</span></span>
        <span>pontos: <b>${u.saldoPontos}</b></span>
        <span>média: <b>${u.mediaAvaliacoes}</b></span>
        <span>github: ${esc(u.githubUsername || '—')}</span>
        <span>tecnologias: ${esc((u.tecnologiasDominadas || []).join(', ') || '—')}</span>
      </div>
      <div class="meta">JWT: ${maskJwt(getSession().token)} · expira ${fmtData(getSession().expiraEm)}</div>
    `;
  }

  return { render: renderSessao };
}