// panes/requestlog.js — painel "Requisições" (a bancada): mostra método,
// endpoint, status HTTP, duração, corpo enviado, resposta e erros.

import { onLog } from '../api.js';
import { esc, jsonPre, fmtData } from '../ui.js';

export function init(root) {
  let container = null;

  const wrap = document.createElement('div');
  wrap.innerHTML = `
    <h2>Requisições</h2>
    <div id="request-log"></div>
    <button id="log-clear" class="ghost tiny">limpar</button>
  `;
  root.innerHTML = '';
  root.append(wrap);

  container = wrap.querySelector('#request-log');
  wrap.querySelector('#log-clear').addEventListener('click', () => { container.innerHTML = ''; });

  onLog((entry) => adicionar(entry));

  function adicionar(entry) {
    const cls = entry.ok ? 'ok' : (entry.status >= 500 ? 'err' : 'err');
    const statusCls = `s${Math.floor(entry.status / 100)}`;
    const statusTxt = entry.status ? String(entry.status) : '—';

    const node = document.createElement('div');
    node.className = `req ${cls}`;

    const reqBody = entry.reqBody !== undefined
      ? `<div class="corpo"><div class="meta">corpo:</div>${jsonPre(entry.reqBody)}</div>` : '';

    const resBody = entry.data !== undefined && (entry.data !== null)
      ? `<div class="corpo"><div class="meta">resposta:</div>${jsonPre(entry.data)}</div>` : '';

    const errBody = entry.error
      ? `<div class="corpo meta" style="color:var(--err)">erro: ${esc(entry.error)}</div>` : '';

    const loc = entry.location
      ? `<div class="meta">Location: <span class="mono">${esc(entry.location)}</span></div>` : '';

    node.innerHTML = `
      <div class="linha">
        <span class="metodo">${esc(entry.method)}</span>
        <span class="status ${statusCls}">${statusTxt}</span>
        <span class="url">${esc(entry.path)}</span>
        <span class="dur">${entry.durationMs}ms${entry.label ? ' · ' + esc(entry.label) : ''}</span>
      </div>
      <div class="meta">${fmtData(entry.at)}</div>
      ${reqBody}${resBody}${errBody}${loc}
    `;
    container.prepend(node);
    // limita o painel a 60 entradas para não pesar
    while (container.children.length > 60) container.lastElementChild.remove();
  }
}