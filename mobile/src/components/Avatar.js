/**
 * components/Avatar.js
 * ---------------------------------------------------------------------------
 * Avatar circular do dev: tenta carregar a foto (avatarUrl) e, se não houver
 * (null/'' — o backend pode não ter), cai no fallback com as iniciais do nome.
 *
 * CONCEITO (React):
 * - Componente "burro": só recebe o que precisa por PROPS (nome, avatarUrl).
 *   Não busca nada, não guarda estado — só renderiza. É a regra de ouro:
 *   componente burro + lógica na camada de service/hook.
 */

import { Image, StyleSheet, Text, View } from 'react-native';

/**
 * @param {String} props.nome       nome do dev (para as iniciais)
 * @param {String} props.avatarUrl  URL da foto (pode ser vazia/null)
 * @param {Number} props.size       diâmetro em px (default 72)
 */
export default function Avatar({ nome = '', avatarUrl, size = 72 }) {
  const temFoto = Boolean(avatarUrl);

  // Iniciais: "Ana Souza" -> "AS". Tolerante a nomes sem sobrenome.
  const iniciais = nome
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((parte) => parte[0]?.toUpperCase() ?? '')
    .join('');

  const raio = size / 2;

  if (temFoto) {
    return (
      <Image
        source={{ uri: avatarUrl }}
        style={[styles.foto, { width: size, height: size, borderRadius: raio }]}
      />
    );
  }

  return (
    <View
      style={[
        styles.fallback,
        { width: size, height: size, borderRadius: raio },
      ]}
    >
      <Text style={[styles.iniciais, { fontSize: size * 0.36 }]}>{iniciais}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  foto: {
    backgroundColor: '#EDE9FE',
  },
  fallback: {
    backgroundColor: '#7C3AED',
    alignItems: 'center',
    justifyContent: 'center',
  },
  iniciais: {
    color: '#FFFFFF',
    fontWeight: '800',
  },
});