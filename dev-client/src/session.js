// session.js — estado da sessão (JWT + usuário autenticado).
//
// Guarda o token no localStorage (ferramenta de DEV) e injeta o Bearer nas
// chamadas REST via api.setAuthToken. Emite eventos `devsos:session` para
// o cabeçalho atualizar o chip de sessão.

import { authApi, setAuthToken, getAuthToken } from './api.js';

const STORAGE_KEY = 'devsos.dev.session';

let state = {
  token: null,
  usuario: null,
  expiraEm: null, // timestamp (ms) de expiração
};

let listeners = [];

export function onSessionChange(fn) { listeners.push(fn); }

function emit() {
  listeners.forEach((fn) => { try { fn(state); } catch (e) { console.error(e); } });
}

function persist() {
  if (state.token) {
    const exp = state.expiraEm ? new Date(state.expiraEm).toISOString() : null;
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: state.token, usuario: state.usuario, expiraEm: exp }));
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
}

function aplicar(token, usuario, expiraEmSegundos) {
  state.token = token;
  state.usuario = usuario;
  state.expiraEm = expiraEmSegundos ? Date.now() + expiraEmSegundos * 1000 : null;
  setAuthToken(token);
  persist();
  emit();
}

export function getSession() { return state; }

export function carregar() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const saved = JSON.parse(raw);
    if (!saved?.token) return;
    const expiraEm = saved.expiraEm ? new Date(saved.expiraEm).getTime() : null;
    if (expiraEm && expiraEm < Date.now()) {
      // token expirado no papel — não restaura
      localStorage.removeItem(STORAGE_KEY);
      return;
    }
    state = { token: saved.token, usuario: saved.usuario || null, expiraEm };
    setAuthToken(saved.token);
    emit();
  } catch (e) {
    console.warn('Sessão inválida no localStorage:', e);
    localStorage.removeItem(STORAGE_KEY);
  }
}

export async function login(email, senha) {
  const r = await authApi.login(email, senha);
  if (r.ok) aplicar(r.data.token, r.data.usuario, r.data.expiraEmSegundos);
  return r;
}

export async function registrar({ nome, email, senha, githubUsername }) {
  const r = await authApi.registrar(nome, email, senha, githubUsername);
  if (r.ok) aplicar(r.data.token, r.data.usuario, r.data.expiraEmSegundos);
  return r;
}

export function logout() {
  state = { token: null, usuario: null, expiraEm: null };
  setAuthToken(null);
  persist();
  emit();
}

export const estaAutenticado = () => Boolean(state.token && (!state.expiraEm || state.expiraEm > Date.now()));
export const tokenAtual = () => state.token;
export { getAuthToken };