// api.js — cliente HTTP do Dev Client Web.
//
// Fala com o backend Spring do devSOS usando fetch. Toda chamada registra um
// "log de requisição" (método, endpoint, status, corpo e resposta) no painel
// à direita, via evento `devsos:log` — a "bancada de laboratório".

import { sanitize } from './ui.js';

const STORAGE_URL = 'devsos.dev.api';
const DEFAULT_URL = import.meta.env.VITE_DEV_SOS_API || 'http://localhost:8080';

let baseUrl = localStorage.getItem(STORAGE_URL) || DEFAULT_URL;
let token = null;                 // JWT em memória (setado pelo session.js)
let logListeners = [];            // painel que exibe o log

export function getBaseUrl() { return baseUrl.replace(/\/+$/, ''); }
export function setBaseUrl(url) {
  baseUrl = (url || DEFAULT_URL).replace(/\/+$/, '');
  localStorage.setItem(STORAGE_URL, baseUrl);
}
export function getAuthToken() { return token; }
export function setAuthToken(t) { token = t; }

export function onLog(fn) { logListeners.push(fn); }

let seq = 0;
function notifyLog(entry) {
  logListeners.forEach((fn) => { try { fn(entry); } catch (e) { console.error(e); } });
}

/**
 * Executa uma requisição REST e registra no log.
 * @returns {{ok:boolean, status:number, data:any, error:string, location:string|null, durationMs:number}}
 */
export async function request(method, path, body, label) {
  const start = performance.now();
  const url = getBaseUrl() + path;
  const entry = {
    id: ++seq, at: new Date(), method, path, url, label: label || '',
    reqBody: body, status: 0, error: null, data: null, location: null, durationMs: 0,
  };
  try {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;

    const res = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    entry.status = res.status;
    entry.location = res.headers.get('Location');

    const text = await res.text();
    entry.data = text ? safeParse(text) : null;

    if (!res.ok) {
      entry.error = mensagemErro(entry.data, res.status, text);
    }
    entry.ok = res.ok;
  } catch (err) {
    entry.ok = false;
    entry.error = err?.message === 'Failed to fetch'
      ? `Sem conexão com ${url}. Verifique se o backend está de pé e a URL.`
      : String(err?.message || err);
  } finally {
    entry.durationMs = Math.round(performance.now() - start);
    notifyLog(entry);
  }
  return entry;
}

function safeParse(text) {
  try { return JSON.parse(text); } catch { return text; }
}

/** Extrai a mensagem amigável do envelope ApiError do backend. */
export function mensagemErro(data, status, raw) {
  if (data && typeof data === 'object') {
    if (data.message) return data.message;
    if (data.fieldErrors) {
      return Object.entries(data.fieldErrors).map(([f, m]) => `${f}: ${m}`).join(' · ');
    }
  }
  return `HTTP ${status}${raw ? ` — ${raw.slice(0, 300)}` : ''}`;
}

/* ------------------------------------------------------------------ */
/*  Endpoints (mesmos contratos de docs/API.md)                        */
/* ------------------------------------------------------------------ */

export const authApi = {
  registrar: (nome, email, senha, githubUsername) =>
    request('POST', '/api/auth/register', { nome, email, senha, githubUsername }, 'Cadastro'),
  login: (email, senha) =>
    request('POST', '/api/auth/login', { email, senha }, 'Login'),
};

export const usersApi = {
  perfil: (id) => request('GET', `/api/users/${id}`, undefined, 'Perfil'),
  atualizarTecnologias: (id, tecnologiasDominadas) =>
    request('PATCH', `/api/users/${id}/technologies`, { tecnologiasDominadas }, 'Tecnologias'),
};

export const postsApi = {
  feed: (page = 0, size = 50) =>
    request('GET', `/api/posts?page=${page}&size=${size}&sort=createdAt,desc`, undefined, 'Feed'),
  criar: (payload) => request('POST', '/api/posts', payload, 'Criar post'),
};

export const sessionsApi = {
  aceitar: (postId) => request('POST', '/api/sessions', { postId }, 'Aceitar socorro'),
  minhas: (page = 0, size = 100) =>
    request('GET', `/api/sessions?page=${page}&size=${size}&sort=createdAt,desc`, undefined, 'Minhas corridas'),
  detalhar: (id) => request('GET', `/api/sessions/${id}`, undefined, 'Detalhe corrida'),
  atualizarStatus: (id, status) =>
    request('PATCH', `/api/sessions/${id}`, { status }, `Status → ${status}`),
  historico: (id) => request('GET', `/api/sessions/${id}/messages`, undefined, 'Histórico'),
};

export const reviewsApi = {
  recebidas: (page = 0, size = 50) =>
    request('GET', `/api/reviews?page=${page}&size=${size}&sort=createdAt,desc`, undefined, 'Avaliações recebidas'),
  avaliar: ({ sessionId, nota, comentario }) =>
    request('POST', '/api/reviews', { sessionId, nota, comentario }, 'Avaliar corrida'),
};

export { sanitize };