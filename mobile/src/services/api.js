/**
 * services/api.js
 * ---------------------------------------------------------------------------
 * O "RestTemplate" / "Feign client" do React Native.
 *
 * ANALOGIA (backend):
 * - axios.create({ baseURL }) = configurar um WebClient/RestTemplate com o
 *   endereço base do servidor (application.properties).
 * - Interceptor de request = JwtAuthenticationFilter (injeta o Bearer antes
 *   de cada chamada, igual o filtro faz no backend).
 * - async/await = obter Future.get(): você dispara uma chamada IO, espera
 *   o resultado, e se der ruim o fluxo cai no catch (como @ExceptionHandler).
 * - Os arquivos de service (feedApi, postsApi...) são os equivalents dos
 *   Controllers REST do backend — só que do lado do cliente.
 */

import axios from 'axios';
import { API_BASE_URL } from '../config';

// ---------------------------------------------------------------------------
// Token em memória
// ---------------------------------------------------------------------------
// "Variável de requisição" (como um atributo de request-scoped bean). Em React,
// não existe "escopo de requisição" — por isso guardamos num closure simples.
// O interceptor abaixo lê destes valores antes de CADA chamada HTTP.

let _token = null;

export function setAuthToken(token) {
  _token = token;
}

export function getAuthToken() {
  return _token;
}

// ---------------------------------------------------------------------------
// Instância HTTP (o "bean" que fala com o backend)
// ---------------------------------------------------------------------------

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

/**
 * Interceptor de request — analogous ao JwtAuthenticationFilter.
 *
 * Toda chamada que usar esta instância passa por aqui ANTES de ser enviada.
 * Se houver token, injeta o cabeçalho `Authorization: Bearer <token>`.
 * No backend, o filtro faz exatamente a mesma coisa (ler o header e colocar
 * o usuario no SecurityContext).
 */
api.interceptors.request.use((config) => {
  if (_token) {
    config.headers.Authorization = `Bearer ${_token}`;
  }
  return config;
});

// ---------------------------------------------------------------------------
// Helpers de tratamento de erro
// ---------------------------------------------------------------------------

/**
 * Extrai a mensagem amigável de um erro de chamada REST.
 *
 * O backend Spring devolve `{ message: "...", fieldErrors: {...} }` via
 * GlobalExceptionHandler. Esta função pega essa mensagem diretamente.
 */
export function extrairErro(error) {
  if (error?.response?.data?.message) return error.response.data.message;
  if (error?.response?.status === 0 || error?.message === 'Network Error')
    return 'Servidor indisponível. Verifique se o backend está rodando e o IP está correto.';
  if (error?.message) return error.message;
  return 'Erro inesperado.';
}

// ---------------------------------------------------------------------------
// Endpoints REST
// ---------------------------------------------------------------------------

/**
 * Autenticação (rota pública — não precisa de token).
 *
 * ANALOGIA: POST /api/auth/login é o login do garçom (o "garçom" devolve
 * um crachá/cupom JWT que o cliente usa em todas as próximas visitas).
 */
export const authApi = {
  login: async (email, senha) => {
    const { data } = await api.post('/api/auth/login', { email, senha });
    return data; // { token, expiraEmSegundos, usuario }
  },
};

/**
 * Feed / posts — o "catálogo de problemas".
 *
 * IMPORTANTE: a resposta é paginada (PagedModel). Para pegar a lista de posts
 * use data.content (cada chamada devolve uma página).
 *
 * ANALOGIA: o feed é como um SELECT com ORDER BY createdAt DESC + LIMIT/OFFSET.
 */
export const feedApi = {
  buscar: async (page = 0, size = 20) => {
    const { data } = await api.get('/api/posts', {
      params: { page, size, sort: 'createdAt,desc' },
    });
    return data.content; // array de PostResponseDTO
  },
};

/**
 * Criação de post — POST /api/posts (precisa de token).
 *
 * O backend NÃO aceita authorId no corpo — ele lê do JWT (nunca confie no que
 * o cliente digita sobre identidade).
 */
export const postsApi = {
  criar: async ({ titulo, descricao, tags = [], tipo = 'FREE', recompensaValor = 0, mediaUrl = '' }) => {
    const { data } = await api.post('/api/posts', {
      titulo,
      descricao,
      tags,
      tipo,
      recompensaValor,
      mediaUrl,
    });
    return data;
  },
};

/**
 * Corridas / sessões — POST para aceitar um socorro.
 *
 * Só manda o postId; o backend define helper (do JWT), status e chatRoomId.
 *
 * ANALOGIA: aceitar socorro = INSERT INTO sessions (helper, post, status)
 * com DEFAULTs, tudo validado no nível de aplicação.
 */
export const sessionsApi = {
  aceitarSocorro: async (postId) => {
    const { data } = await api.post('/api/sessions', { postId });
    return data; // SessionResponseDTO com chatRoomId
  },

  historico: async (sessionId) => {
    const { data } = await api.get(`/api/sessions/${sessionId}/messages`);
    return data; // array de ChatMessageDTO
  },
};