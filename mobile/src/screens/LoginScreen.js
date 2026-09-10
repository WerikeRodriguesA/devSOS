/**
 * screens/LoginScreen.js
 * ---------------------------------------------------------------------------
 * Tela de entrada. Autentica no backend e "abre a porta" para o feed.
 *
 * (O MVP precisa de login porque aceitar socorro e entrar no chat exigem JWT.
 * O padrão de validação é o mesmo da API: o backend devolve mensagens
 * amigáveis via GlobalExceptionHandler e nós as exibimos como estão.)
 */

import { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useAuth } from '../hooks/AuthContext';

export default function LoginScreen({ navegar }) {
  const { login } = useAuth();

  // Estado do formulário (analogia: são os parâmetros do DTO LoginRequest)
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [mensagemErro, setMensagemErro] = useState(null);

  const aoSubmit = async () => {
    setMensagemErro(null);
    const resultado = await login(email, senha);
    if (resultado.ok) {
      navegar(require('./FeedScreen').default);
    } else {
      setMensagemErro(resultado.mensagem);
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={styles.container}
    >
      <View style={styles.card}>
        <Text style={styles.titulo}>devSOS</Text>
        <Text style={styles.subtitulo}>Quem sabe ajuda quem precisa.</Text>

        <Text style={styles.label}>E-mail</Text>
        <TextInput
          style={styles.input}
          value={email}
          onChangeText={setEmail}
          placeholder="voce@email.com"
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
        />

        <Text style={styles.label}>Senha</Text>
        <TextInput
          style={styles.input}
          value={senha}
          onChangeText={setSenha}
          placeholder="******"
          secureTextEntry
        />

        {mensagemErro && <Text style={styles.erro}>{mensagemErro}</Text>}

        <Pressable style={styles.botao} onPress={aoSubmit}>
          {({ pressed }) => (
            <View style={[styles.botaoView, pressed && styles.botaoAtivo]}>
              <ActivityIndicator size="small" color="#FFFFFF" animating={false} />
              <Text style={styles.botaoTexto}>Entrar</Text>
            </View>
          )}
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F6F0FF',
    justifyContent: 'center',
  },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 24,
    marginHorizontal: 24,
  },
  titulo: {
    fontSize: 32,
    fontWeight: '800',
    color: '#4B3869',
    textAlign: 'center',
  },
  subtitulo: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    marginBottom: 20,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: '#374151',
    marginBottom: 4,
  },
  input: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    marginBottom: 14,
  },
  erro: {
    color: '#B91C1C',
    fontSize: 13,
    marginBottom: 10,
  },
  botao: {
    backgroundColor: '#7C3AED',
    borderRadius: 10,
    marginTop: 6,
  },
  botaoView: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 13,
  },
  botaoAtivo: {
    opacity: 0.7,
  },
  botaoTexto: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 16,
  },
});