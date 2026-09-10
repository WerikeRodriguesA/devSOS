/**
 * components/MessageBubble.js
 * ---------------------------------------------------------------------------
 * Como o nome diz: uma "bolha" de mensagem do chat.
 *
 * DUAS FORMAS DE RENDERIZAR (condicional):
 * - type 'CHAT'       -> balão normal com texto livre.
 * - type 'CODE_SNIPPET' -> bloco escuro com fonte monoespaçada (código).
 *
 * A mesma entrada (props) gera saídas diferentes dependendo do conteúdo —
 * pensando em backend, é como um factory/switch que decide a representação
 * (o "response render" muda de acordo com o tipo do DTO).
 */

import { StyleSheet, Text, View } from 'react-native';

/**
 * @param {Object} props
 * @param {Object} props.mensagem  ChatMessageDTO (senderId, senderNome, content, timestamp, type)
 * @param {boolean} props.minha    true se a mensagem é do usuário logado
 */
export default function MessageBubble({ mensagem, minha = false }) {
  const ehCodigo = mensagem.type === 'CODE_SNIPPET';

  return (
    <View style={[styles.linha, minha ? styles.linhaMinha : styles.linhaOutra]}>
      <View
        style={[
          styles.balao,
          minha ? styles.balaoMinha : styles.balaoOutra,
          ehCodigo && styles.balaoCodigo,
        ]}
      >
        {!minha && <Text style={styles.remetente}>{mensagem.senderNome}</Text>}

        {ehCodigo ? (
          <Text style={styles.codigo}>
            {mensagem.content}
          </Text>
        ) : (
          <Text style={[styles.texto, minha && styles.textoMinha]}>
            {mensagem.content}
          </Text>
        )}

        <Text style={styles.hora}>
          {new Date(mensagem.timestamp).toLocaleTimeString('pt-BR', {
            hour: '2-digit',
            minute: '2-digit',
          })}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  linha: {
    flexDirection: 'row',
    marginHorizontal: 16,
    marginVertical: 3,
  },
  linhaMinha: {
    justifyContent: 'flex-end',
  },
  linhaOutra: {
    justifyContent: 'flex-start',
  },
  balao: {
    maxWidth: '78%',
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  balaoMinha: {
    backgroundColor: '#7C3AED',
    borderBottomRightRadius: 4,
  },
  balaoOutra: {
    backgroundColor: '#FFFFFF',
    borderBottomLeftRadius: 4,
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  balaoCodigo: {
    maxWidth: '92%',
    backgroundColor: '#0F172A',
  },
  remetente: {
    fontSize: 11,
    fontWeight: '700',
    color: '#7C3AED',
    marginBottom: 2,
  },
  texto: {
    fontSize: 15,
    color: '#111827',
  },
  textoMinha: {
    color: '#FFFFFF',
  },
  codigo: {
    fontFamily: 'monospace',
    fontSize: 13,
    color: '#A5F3FC',
    lineHeight: 19,
  },
  hora: {
    alignSelf: 'flex-end',
    marginTop: 4,
    fontSize: 10,
    color: '#9CA3AF',
  },
});