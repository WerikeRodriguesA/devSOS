/**
 * screens/CreatePostScreen.js
 * ---------------------------------------------------------------------------
 * Formulário para publicar uma dúvida: título, descrição e tags.
 *
 * FLUXO ASSÍNCRONO EXPLICADO:
 * - `onSubmit` é uma função `async/await`: o clique vira uma Promise que
 *   espera a resposta do POST /api/posts.
 *   try/catch/finally = @ExceptionHandler: sucesso vai pro feed, erro mostra
 *   a mensagem do backend (ApiError.message) sem travar o app.
 */

import { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
} from 'react-native';
import { extrairErro, postsApi } from '../services/api';

export default function CreatePostScreen({ navegar }) {
  // Estado do formulário (análogo ao PostCreateRequestDTO preenchido)
  const [titulo, setTitulo] = useState('');
  const [descricao, setDescricao] = useState('');
  const [tagsTexto, setTagsTexto] = useState(''); // ex.: "java, spring, postgres"
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState(null);

  /**
   * Converte "java, spring" -> ["java","spring"].
   * ((Relembrando) o backend aceita tags como um ARRAY de até 10.)
   */
  const extrairTags = () =>
    tagsTexto
      .split(',')
      .map((t) => t.trim())
      .filter(Boolean)
      .slice(0, 10);

  const publicar = async () => {
    setErro(null);
    setEnviando(true);
    try {
      await postsApi.criar({
        titulo,
        descricao,
        tags: extrairTags(),
        tipo: 'FREE',
      });
      // Volta para o feed, que recarrega ao montar de novo.
      navegar(require('./FeedScreen').default);
    } catch (e) {
      setErro(extrairErro(e));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.titulo}>Publicar dúvida</Text>

        <Text style={styles.label}>Título (3 a 160 caracteres)</Text>
        <TextInput
          style={styles.input}
          value={titulo}
          onChangeText={setTitulo}
          maxLength={160}
          placeholder="Ex.: Circular dependency no Spring Boot"
        />

        <Text style={styles.label}>Descrição (10 a 5000 caracteres)</Text>
        <TextInput
          style={[styles.input, styles.area]}
          value={descricao}
          onChangeText={setDescricao}
          multiline
          numberOfLines={6}
          placeholder="Descreva o problema, erre, o que já tentou..."
        />

        <Text style={styles.label}>Tags (separadas por vírgula)</Text>
        <TextInput
          style={styles.input}
          value={tagsTexto}
          onChangeText={setTagsTexto}
          placeholder="java, spring, postgres"
        />

        {erro && <Text style={styles.erro}>{erro}</Text>}

        <Pressable
          style={[styles.botao, enviando && styles.botaoAtivo]}
          onPress={publicar}
          disabled={enviando}
        >
          <ActivityIndicator size="small" color="#FFFFFF" animating={enviando} />
          <Text style={styles.botaoTexto}>Publicar</Text>
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F6F0FF' },
  content: { padding: 20 },
  titulo: { fontSize: 22, fontWeight: '800', color: '#4B3869', marginBottom: 16 },
  label: { fontSize: 13, fontWeight: '600', color: '#374151', marginBottom: 4 },
  input: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    backgroundColor: '#FFFFFF',
    marginBottom: 14,
  },
  area: { minHeight: 120, textAlignVertical: 'top' },
  erro: { color: '#B91C1C', fontSize: 13, marginBottom: 10 },
  botao: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#7C3AED',
    borderRadius: 10,
    paddingVertical: 13,
    marginTop: 6,
  },
  botaoAtivo: { opacity: 0.6 },
  botaoTexto: { color: '#FFFFFF', fontWeight: '700', fontSize: 16 },
});