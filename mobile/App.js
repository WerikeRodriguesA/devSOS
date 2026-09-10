/**
 * App.js
 * ---------------------------------------------------------------------------
 * Ponto de entrada do DevSOS Mobile.
 *
 * AQUI ACONTECE A "NAVEGAÇÃO MANUAL" (para o MVP):
 * - Em vez de adicionar a lib react-navigation, usamos um estado `rota` que
 *   guarda { Tela, params }. Trocar a rota = trocar o componente renderizado.
 *
 * ANALOGIA (backend):
 * - <AuthProvider> é como o @EnableWebSecurity: "acende" a infraestrutura
 *   global (aqui, o Context de autenticação) uma única vez no boot.
 * - O state `rota` é o "dispatcher de telas": funciona como um
 *   Controlador que decide qual View responder (ex.: /feed -> FeedScreen).
 *
 * POR QUE NÃO react-navigation JÁ NO MVP?
 * Para separar o ensino dos CONCEITOS do unusedz das bibliotecas. Na prática
 * de mercado, o react-navigation é o padrão — mantemos aqui o mais simples
 * possível para o foco do estudo, e isso fica documentado.
 */

import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { SafeAreaView, StyleSheet } from 'react-native';
import { AuthProvider } from './src/hooks/AuthContext';
import LoginScreen from './src/screens/LoginScreen';
import FeedScreen from './src/screens/FeedScreen';
import CreatePostScreen from './src/screens/CreatePostScreen';
import ChatScreen from './src/screens/ChatScreen';

/**
 * "Roteador": um switch de telas feito de useState.
 */
function Navegador() {
  const [rota, setRota] = useState({ Tela: LoginScreen, params: null });

  const navegar = (Tela, params) => setRota({ Tela, params });

  const TelaAtual = rota.Tela;

  return (
    <SafeAreaView style={styles.container}>
      <TelaAtual navegar={navegar} params={rota.params} />
      <StatusBar style="auto" />
    </SafeAreaView>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <Navegador />
    </AuthProvider>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F6F0FF' },
});