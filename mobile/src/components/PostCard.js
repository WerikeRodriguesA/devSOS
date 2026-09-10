/**
 * components/PostCard.js
 * ---------------------------------------------------------------------------
 * O card do feed — "estilo Instagram" do DevSOS.
 *
 * O QUE É UMA PROP?
 * Props são os PARÂMETROS que o componente recebe do pai. O componente é
 * como um método: recebe argumentos (props) e "retorna" uma renderização.
 *
 * Como em Java, melhores práticas:
 * - Escreva o tipo/contrato dos props no JSDoc (@param).
 * - O componente NÃO decide o que fazer ao clicar em "Aceitar": ele SÓ
 *   notifica o pai via callback (onAceitar). Isso mantém o componente burro
 *   e reutilizável — como um DTO que o controller decide como usar.
 */

import { Pressable, StyleSheet, Text, View } from 'react-native';

/**
 * @param {Object} props
 * @param {Object} props.post        PostResponseDTO (id, titulo, descricao, tags, ...)
 * @param {boolean} props.aceitando  true enquanto o aceite está em andamento
 * @param {Function} props.onAceitar callback(post) disparado ao tocar o botão
 */
export default function PostCard({ post, aceitando = false, onAceitar = () => {} }) {
  const podeAceitar = post.status === 'OPEN';
  const ehPago = post.tipo === 'PAID' && post.recompensaValor > 0;

  return (
    <View style={styles.card}>
      {/* Cabeçalho do card: autor + recompensa */}
      <View style={styles.autorLinha}>
        <Text style={styles.autor}>{post.autorNome}</Text>
        <Text style={[styles.recompensa, ehPago ? styles.recompensaPaga : null]}>
          {ehPago ? `R$ ${post.recompensaValor}` : 'Gratuito'}
        </Text>
      </View>

      <Text style={styles.titulo}>{post.titulo}</Text>
      <Text style={styles.descricao} numberOfLines={4}>
        {post.descricao}
      </Text>

      {/* Tags: chips de tempo de compilação */}
      <View style={styles.tagsLinha}>
        {(post.tags || []).map((tag) => (
          <View key={tag} style={styles.tag}>
            <Text style={styles.tagTexto}>#{tag}</Text>
          </View>
        ))}
      </View>

      {podeAceitar && (
        <Pressable
          style={({ pressed }) => [
            styles.botao,
            (pressed || aceitando) && styles.botaoAtivo,
          ]}
          disabled={aceitando}
          onPress={() => onAceitar(post)}
        >
          <Text style={styles.botaoTexto}>
            {aceitando ? 'Aceitando...' : 'Aceitar Socorro'}
          </Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 16,
    marginHorizontal: 16,
    marginVertical: 8,
    shadowColor: '#000',
    shadowOpacity: 0.08,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  autorLinha: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  autor: {
    fontSize: 14,
    fontWeight: '600',
    color: '#4B3869',
  },
  recompensa: {
    fontSize: 12,
    fontWeight: '700',
    color: '#7C3AED',
    backgroundColor: '#F3E8FF',
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: 999,
    overflow: 'hidden',
  },
  recompensaPaga: {
    color: '#065F46',
    backgroundColor: '#D1FAE5',
  },
  titulo: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 6,
  },
  descricao: {
    fontSize: 14,
    color: '#4B5563',
    lineHeight: 20,
    marginBottom: 12,
  },
  tagsLinha: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginBottom: 12,
  },
  tag: {
    backgroundColor: '#EEF2FF',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  tagTexto: {
    fontSize: 12,
    color: '#3730A3',
  },
  botao: {
    backgroundColor: '#7C3AED',
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
  },
  botaoAtivo: {
    opacity: 0.6,
  },
  botaoTexto: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 15,
  },
});