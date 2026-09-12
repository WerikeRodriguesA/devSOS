// ui.js — helpers de DOM e formatação compartilhados pelas seções.

export function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

export function fmtData(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleString('pt-BR');
}

export function maskJwt(v) {
  if (typeof v !== 'string') return v;
  // JWT: header.payload.signature (3 partes com ponto)
  if (v.split('.').length >= 2 && v.length > 24) {
    return `${v.slice(0, 18)}...${v.slice(-6)}`;
  }
  return v;
}

/** Oculta campos sensíveis (token/senha) em objetos antes de logar. */
export function sanitize(obj) {
  if (!obj || typeof obj !== 'object') return obj;
  if (Array.isArray(obj)) return obj.map(sanitize);
  const out = {};
  for (const [k, v] of Object.entries(obj)) {
    if (/token|senha|password/i.test(k)) out[k] = maskJwt(v);
    else out[k] = v && typeof v === 'object' ? sanitize(v) : v;
  }
  return out;
}

export function jsonPre(s) {
  let text;
  try {
    text = JSON.stringify(sanitize(s), null, 2);
  } catch {
    text = String(s);
  }
  return `<pre class="json">${esc(text)}</pre>`;
}

export function statusChip(status) {
  const cls = {
    OPEN: 'badge-ok', IN_PROGRESS: 'badge-info', RESOLVED: 'badge-ok', CANCELLED: 'badge-err',
    MATCHED: 'badge-warn', ACTIVE: 'badge-info', COMPLETED: 'badge-ok',
  }[status] || '';
  return `<span class="chip ${cls}">${esc(status)}</span>`;
}

export function rotuloStatus(status) {
  return { MATCHED: 'Combinada', ACTIVE: 'Em andamento', COMPLETED: 'Concluída', CANCELLED: 'Cancelada' }[status] || status;
}

/** Cria um elemento DOM a partir de uma spec simples { tag, cls, text, html, attrs, on } */
export function el({ tag = 'div', cls, text, html, attrs, on }, children = []) {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  if (html != null) node.innerHTML = html;
  if (attrs) for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  if (on) for (const [ev, fn] of Object.entries(on)) node.addEventListener(ev, fn);
  (Array.isArray(children) ? children : [children]).filter(Boolean).forEach((c) => {
    node.append(typeof c === 'string' ? document.createTextNode(c) : c);
  });
  return node;
}

export function grupo(labelTxt, inputEl) {
  const wrap = document.createElement('label');
  wrap.className = 'grupo';
  const lab = document.createElement('span');
  lab.textContent = labelTxt;
  wrap.append(lab, inputEl);
  return wrap;
}

/** <pre class="json"> a partir de qualquer valor. */
export function objectHtml(obj) {
  const pre = document.createElement('pre');
  pre.className = 'json';
  pre.textContent = JSON.stringify(sanitize(obj), null, 2);
  return pre;
}

/** <button> com evento e variante (ok | danger | info | ghost tiny). */
export function buttonHtml(label, onClick, variant = 'ghost tiny', disabled = false) {
  const b = document.createElement('button');
  b.className = variant || '';
  b.textContent = label;
  b.disabled = disabled;
  b.addEventListener('click', onClick);
  return b;
}

/** Parsea um HTML simples e devolve o primeiro elemento. */
export function domHtml(html) {
  const tmp = document.createElement('div');
  tmp.innerHTML = html;
  return tmp.firstElementChild;
}