/**
 * screens/ChatScreen.js
 * ---------------------------------------------------------------------------
 * A "Corrida do Uber" do DevSOS: sala de chat em tempo real.
 *
 * FLUXO:
 * 1. Ao abrir, o hook useChatMessages carrega o histórico (REST) e conecta
 *    o WebSocket assinando /topic/chat/{chatRoomId}.
 * 2. As mensagens viram balões (MessageBubble), diferenciando CHAT de
 *    CODE_SNIPPET.
 * 3. O input envia via STOMP (SEND /app/chat/{chatRoomId}).
 *
 * CONCEITOS:
 * - Lista invertida (inverted): o React Native deixa o "final" da lista
 *   ancorado no rodapé — é o padrão de chat. Dado que invertemos a lista,
 *   passamos as mensagens ao contrário.
 * - KeyboardAvoidingView: evita que o teclado cubra o input (como o backend
 *   "evita overflow" ao calcular limites — aqui é só layout).
 */

import { useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import MessageBubble from '../components/MessageBubble';
import { useAuth } from '../hooks/AuthContext';
import { useChatMessages } from '../hooks/useChatMessages';

/**
 * @param {Object} props.navegar   função de navegação (voltar para o feed)
 * @param {Object} props.params    { sessionId, chatRoomId, titulo }
 */
export default function ChatScreen({ navegar, params }) {
  const { sessionId, chatRoomId, titulo } = params;
  const { usuario, token } = useAuth();

  const { mensagens, carregando, conectado, erro, enviar } = useChatMessages({
    sessionId,
    chatRoomId,
    token,
  });

  const [texto, setTexto] = useState('');
  const [tipo, setTipo] = useState('CHAT'); // 'CHAT' | 'CODE_SNIPPET'

  const aoEnviar = () => {
    const conteudo = texto.trim();
    if (!conteudo) return;
    enviar(conteudo, tipo); // o servidor preenche senderId/nome/timestamp
    setTexto('');
  };

  /**
   * keyExtractor sem id no DTO? O ChatMessageDTO não tem `id` (contrato do
   * backend), então geramos uma chave estável a partir dos campos.
   */
  const chaveDaMensagem = (m, index) =>
    `${m.senderId}|${m.timestamp}|${m.content}|${index}`;

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={80}
    >
      {/* Cabeçalho com o título da corrida + status da conexão */}
      <View style={styles.header}>
        <Pressable onPress={() => navegar(require('./FeedScreen').default)}>
          <Text style={styles.voltar}>voltar</Text>
        </Pressable>
        <Text style={styles.titulo} numberOfLines={1}>
          {titulo}
        </Text>
        <View style={[styles.status, conectado ? styles.statusOn : styles.statusOff]}>
          <Text style={styles.statusTexto}>{conectado ? 'ao vivo' : 'offline'}</Text>
        </View>
      </View>

      {erro && <Text style={styles.erro}>{erro}</Text>}

      {carregando ? (
        <View style={styles.carregando}>
          <ActivityIndicator size="large" color="#7C3AED" />
          <Text style={styles.carregandoTexto}>Carregando histórico...</Text>
        </View>
      ) : (
        <FlatList
          style={styles.lista}
          data={[...mensagens].reverse()}
          keyExtractor={chaveDaMensagem}
          inverted
          renderItem={({ item }) => (
            <MessageBubble
              mensagem={item}
              minha={item.senderId === usuario?.id}
            />
          )}
          ListEmptyComponent={
            <Text style={styles.vazio}>Nenhuma mensagem ainda. Comece a conversa!</Text>
          }
        />
      )}

      {/* Input + botão de envio + alternância Texto/Código */}
      <View style={styles.rodape}>
        <View style={styles.tipoLinha}>
          {['CHAT', 'CODE_SNIPPET'].map((t) => (
            <Pressable
              key={t}
              style={[styles.tipoBotao, tipo === t && styles.tipoBotaoOn]}
              onPress={() => setTipo(t)}
            >
              <Text style={[styles.tipoTexto, tipo === t && styles.tipoTextoOn]}>
                {t === 'CHAT' ? 'Texto' : 'Código'}
              </Text>
            </Pressable>
          ))}
        </View>

        <View style={styles.inputLinha}>
          <TextInput
            style={styles.input}
            value={texto}
            onChangeText={setTexto}
            placeholder={tipo === 'CODE_SNIPPET' ? 'Cole seu código aqui...' : 'Mensagem...'}
            multiline
          />
          <Pressable style={styles.enviar} onPress={aoEnviar}>
            <Text style={styles.enviarTexto}>Enviar</Text>
          </Pressable>
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F6F0FF' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
  },
  voltar: { color: '#7C3AED', fontWeight: '700', fontSize: 15 },
  titulo: { flex: 1, fontSize: 16, fontWeight: '800', color: '#1F2937' },
  status: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
  statusOn: { backgroundColor: '#D1FAE5' },
  statusOff: { backgroundColor: '#FEE2E2' },
  statusTexto: { fontSize: 11, fontWeight: '700', color: '#065F46' },
  erro: {
    backgroundColor: '#FEF2F2',
    color: '#B91C1C',
    padding: 10,
  },
  carregando: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8 },
  carregandoTexto: { color: '#6B7280' },
  lista: { flex: 1 },
  vazio: {
    textAlign: 'center',
    color: '#6B7280',
    marginTop: 40,
    paddingHorizontal: 24,
  },
  rodape: {
    backgroundColor: '#FFFFFF',
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  tipoLinha: { flexDirection: 'row', gap: 8, marginBottom: 8 },
  tipoBotao: {
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 8,
    backgroundColor: '#F3F4F6',
  },
  tipoBotaoOn: { backgroundColor: '#7C3AED' },
  tipoTexto: { fontSize: 13, fontWeight: '600', color: '#4B5563' },
  tipoTextoOn: { color: '#FFFFFF' },
  inputLinha: { flexDirection: 'row', alignItems: 'flex-end', gap: 8 },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 9,
    fontSize: 15,
    maxHeight: 100,
  },
  enviar: {
    backgroundColor: '#7C3AED',
    borderRadius: 12,
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  enviarTexto: { color: '#FFFFFF', fontSize: 18 },
});