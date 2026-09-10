/**
 * components/AvaliarCorridaModal.js
 * ---------------------------------------------------------------------------
 * Modal de avaliação mútua: abre sobre a "Corrida Concluída" e envia o POST
 * /api/reviews (o backend decide QUEM será avaliado: o outro lado da sessão).
 *
 * CONCEITO (React):
 * - <Modal> do React Native desenha uma tela por cima da atual — como uma
 *   "janela modal" do desktop. O conteúdo vive no próprio componente.
 * - Estado local (nota, comentário, erro...) é do formulário; a TELA que
 *   chamou é quem decide o que acontece depois (via onConcluida).
 */

import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { extrairErro, reviewsApi } from '../services/api';
import RatingStars from './RatingStars';

/**
 * @param {Boolean}  props.visivel        controla abertura/fechamento
 * @param {Object}   props.corrida        SessionResponseDTO (a corrida concluída)
 * @param {String}   props.avaliadoNome   nome de QUEM está sendo avaliado
 * @param {Function} props.onFechar       fecha o modal
 * @param {Function} props.onConcluida    chamada após avaliação enviada (refresh)
 */
export default function AvaliarCorridaModal({
  visivel,
  corrida,
  avaliadoNome,
  onFechar,
  onConcluida,
}) {
  const [nota, setNota] = useState(0);
  const [comentario, setComentario] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState(null);
  const [sucesso, setSucesso] = useState(false);

  // Toda vez que o modal ABRE, zera o formulário (como resetar um DTO novo).
  useEffect(() => {
    if (visivel) {
      setNota(0);
      setComentario('');
      setErro(null);
      setSucesso(false);
    }
  }, [visivel]);

  const aoEnviar = async () => {
    if (nota < 1) {
      setErro('Escolha uma nota de 1 a 5.');
      return;
    }
    setEnviando(true);
    setErro(null);
    try {
      await reviewsApi.avaliar({
        sessionId: corrida.id,
        nota,
        comentario: comentario.trim(),
      });
      setSucesso(true);
      if (onConcluida) onConcluida();
    } catch (e) {
      setErro(extrairErro(e));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Modal
      visible={visivel}
      transparent
      animationType="fade"
      onRequestClose={onFechar}
    >
      <View style={styles.fundo}>
        <View style={styles.card}>
          {sucesso ? (
            <View style={styles.sucesso}>
              <Text style={styles.sucessoIcone}>✓</Text>
              <Text style={styles.sucessoTitulo}>Avaliação enviada!</Text>
              <Text style={styles.sucessoSub}>Obrigado por ajudar a comunidade.</Text>
              <Pressable style={styles.botao} onPress={onFechar}>
                <Text style={styles.botaoTexto}>Fechar</Text>
              </Pressable>
            </View>
          ) : (
            <ScrollView showsVerticalScrollIndicator={false}>
              <Text style={styles.titulo}>Avaliar corrida</Text>
              <Text style={styles.assunto} numberOfLines={2}>
                {corrida?.postTitulo}
              </Text>

              <Text style={styles.label}>Como foi ajudar {avaliadoNome}?</Text>
              <View style={styles.estrelas}>
                <RatingStars nota={nota} tamanho="gd" onChange={setNota} />
                <Text style={styles.notaTexto}>{nota > 0 ? `${nota}/5` : 'toque para dar nota'}</Text>
              </View>

              <Text style={styles.label}>Comentário (opcional)</Text>
              <TextInput
                style={styles.comentario}
                value={comentario}
                onChangeText={setComentario}
                placeholder="Contou com você e explicou bem..."
                multiline
                maxLength={1000}
              />

              {erro && <Text style={styles.erro}>{erro}</Text>}

              <View style={styles.botoes}>
                <Pressable style={[styles.botao, styles.botaoSecundario]} onPress={onFechar}>
                  <Text style={styles.botaoSecundarioTexto}>Cancelar</Text>
                </Pressable>
                <Pressable
                  style={[styles.botao, enviando && styles.botaoAtivo]}
                  onPress={aoEnviar}
                  disabled={enviando}
                >
                  {enviando ? (
                    <ActivityIndicator size="small" color="#FFFFFF" />
                  ) : (
                    <Text style={styles.botaoTexto}>Enviar avaliação</Text>
                  )}
                </Pressable>
              </View>
            </ScrollView>
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  fundo: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.45)',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 20,
  },
  titulo: { fontSize: 18, fontWeight: '800', color: '#1F2937' },
  assunto: { fontSize: 14, color: '#6B7280', marginTop: 2, marginBottom: 16 },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: '#374151',
    marginTop: 12,
    marginBottom: 6,
  },
  estrelas: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  notaTexto: { color: '#9CA3AF', fontSize: 13 },
  comentario: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 10,
    padding: 12,
    minHeight: 72,
    fontSize: 14,
    textAlignVertical: 'top',
  },
  erro: { color: '#B91C1C', fontSize: 13, marginTop: 10 },
  botoes: { flexDirection: 'row', gap: 10, marginTop: 18 },
  botao: {
    flex: 1,
    backgroundColor: '#7C3AED',
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  botaoAtivo: { opacity: 0.7 },
  botaoTexto: { color: '#FFFFFF', fontWeight: '700', fontSize: 14 },
  botaoSecundario: {
    backgroundColor: '#F3F4F6',
  },
  botaoSecundarioTexto: { color: '#374151', fontWeight: '700', fontSize: 14 },
  sucesso: { alignItems: 'center', paddingVertical: 18 },
  sucessoIcone: {
    fontSize: 44,
    color: '#059669',
    fontWeight: '800',
  },
  sucessoTitulo: { fontSize: 18, fontWeight: '800', color: '#1F2937', marginTop: 6 },
  sucessoSub: { color: '#6B7280', marginTop: 4, marginBottom: 16, textAlign: 'center' },
});