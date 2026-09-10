/**
 * components/RatingStars.js
 * ---------------------------------------------------------------------------
 * Estrelas de avaliação (1 a 5).
 *
 * Dois modos:
 * - só exibição (padrão): mostra a nota com estrelas cheias/vazias.
 * - edição: passa `onChange` -> cada estrela vira um botão (para avaliar a
 *   corrida). Em edição, `nota` é o valor preenchido até agora.
 *
 * CONCEITO (React):
 * - Um componente pode ter 2 "modos" dependendo de quais PROPS recebe —
 *   como um DTO que responde diferente com base no que foi preenchido.
 */

import { Pressable, StyleSheet, Text, View } from 'react-native';

const TOTAL = 5;

/**
 * @param {Number}  props.nota     valor atual (decimal ok p/ exibição)
 * @param {Boolean} props.tamanho  tamanho das estrelas ('pe' | 'md' | 'gd')
 * @param {Function} props.onChange  se passada, habilita o modo edição
 */
export default function RatingStars({ nota = 0, tamanho = 'md', onChange }) {
  const editavel = typeof onChange === 'function';

  const renderizar = (posicao) => {
    const cheia = posicao <= Math.round(nota);
    if (editavel) {
      return (
        <Pressable
          key={posicao}
          onPress={() => onChange(posicao)}
          hitSlop={4}
        >
          <Text style={[styles.estrela, styles[tamanho], cheia ? styles.cheia : styles.vazia]}>
            ★
          </Text>
        </Pressable>
      );
    }
    return (
      <Text key={posicao} style={[styles.estrela, styles[tamanho], cheia ? styles.cheia : styles.vazia]}>
        ★
      </Text>
    );
  };

  return <View style={styles.linha}>{[1, 2, 3, 4, 5].map(renderizar)}</View>;
}

const styles = StyleSheet.create({
  linha: { flexDirection: 'row', gap: 2, alignItems: 'center' },
  estrela: { color: '#D1D5DB' },
  cheia: { color: '#F59E0B' },
  vazia: { color: '#D1D5DB' },
  pe: { fontSize: 12 },
  md: { fontSize: 18 },
  gd: { fontSize: 30 },
});