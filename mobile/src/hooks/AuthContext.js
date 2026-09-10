/**
 * hooks/AuthContext.js
 * ---------------------------------------------------------------------------
 * Autenticação global do app (quem está logado, qual é o token).
 *
 * ANALOGIA (backend):
 * - No Spring, coisas como `SecurityContextHolder`, `bean` singleton ou
 *   variáveis de request-scope ficam disponíveis para qualquer componente.
 * - No React, o equivalente é o **Context**: um "singleton global" que
 *   qualquer componente pode LER (via useAuth) e que o App registra uma única
 *   vez no topo (o <AuthProvider>).

 * QUANDO USAR CONTEXT E QUANDO USAR PROPS?
 * - Props: passar dados de um componente PAI para o FILHO direto
 *   (como parâmetros de método).
 * - Context: dados que QUASE TODA TELA precisa (token, usuário), sem ficar
 *   "empurrando" manualmente através de dezenas de telas. Isto é como a
 *   injeção de dependência do Spring (@Autowired) que entrega o bean no
 *   lugar certo sem você montá-lo à mão.
 */

import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { authApi, extrairErro, setAuthToken } from '../services/api';

/**
 * Estado vazio do token.
 */
const AuthContext = createContext(null);

/**
 * Provedor: registra o contexto no topo do app (um única vez, no App.js).
 *
 * ANALOGIA: é o @SpringBootApplication que sobe e coloca o ApplicationContext
 * disponível para todas as classes beans.
 */
export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null);
  const [token, setToken] = useState(null);
  const [carregando, setCarregando] = useState(false);

  /**
   * Login: chama o backend, guarda o token e o perfil no estado.
   *
   * ANALOGIA: endpoint POST /api/auth/login valida as credenciais e devolve
   * o AuthResponseDTO { token, expiraEmSegundos, usuario }. Aqui a gente
   * "cola" esse usuário na sessão do app (como um SecurityContext local).
   *
   * Retorno { ok, mensagem }: padroniza o resultado para a tela não precisar
   * saber detalhes de exceção — igual ao ApiError do backend (message amigável).
   */
  const login = useCallback(async (email, senha) => {
    setCarregando(true);
    try {
      const resposta = await authApi.login(email, senha);
      setToken(resposta.token);
      setAuthToken(resposta.token); // injeta o Bearer nas chamadas REST
      setUsuario(resposta.usuario);
      return { ok: true };
    } catch (erro) {
      return { ok: false, mensagem: extrairErro(erro) };
    } finally {
      setCarregando(false);
    }
  }, []);

  /**
   * Logout: limpa o token. ANALOGIA: revoga a sessão (no Spring, limparia
   * o SecurityContext). Como o backend é stateless (JWT), basta o app parar
   * de enviar o header.
   */
  const logout = useCallback(() => {
    setToken(null);
    setUsuario(null);
    setAuthToken(null);
  }, []);

  /**
   * useMemo: recalcula o objeto de contexto só quando algo mudar de verdade.
   * ANALOGIA: evita re-renderização desnecessária — como um cache de entidade.
   */
  const valor = useMemo(
    () => ({ usuario, token, carregando, login, logout }),
    [usuario, token, carregando, login, logout]
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

/**
 * useAuth — o "useContext" que entrega o contexto de autenticação.
 *
 * ANALOGIA: é o @Autowired do frontend. Em vez de você receber a sessão por
 * parâmetro em 40 telas, chama useAuth() e o React te entrega o singleton.
 */
export function useAuth() {
  const contexto = useContext(AuthContext);
  if (!contexto) {
    throw new Error('useAuth deve ser usado dentro de <AuthProvider>.');
  }
  return contexto;
}