/**
 * screens/PerfilScreen.js
 * ---------------------------------------------------------------------------
 * Perfil do dev + "Minhas corridas" + "Avaliações" + logout.
 *
 * FLUXO do usuário:
 * 1. No feed, toca em "Perfil".
 * 2. Vê avatar, bio, techs, saldo de pontos e média de avaliações.
 * 3. Alterna entre as abas "Corridas" e "Avaliações".
 * 4. Numa corrida EM ANDAMENTO abre o chat; numa CONCLUÍDA avalia a corrida.
 * 5. "Sair" encerra a sessão local (backend é stateless — ver AuthContext).
 *
 * CONCEITOS:
 * - ListHeaderComponent: a FlatList tem UM scroll só, e o "cabeçalho" (perfil
 *   + abas) rola junto com a lista — evita o erro de "VirtualizedList dentro
 *   de ScrollView" (dois scrolls aninhados, como CROSS JOIN de listas).
 * - Promise.all: busca perfil + corridas + avaliações em paralelo, como um
 *   batch de queries — menos latência de "round trip".
 */

import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Avatar from '../components/Avatar';
import AvaliarCorridaModal from '../components/AvaliarCorridaModal';
import RatingStars from '../components/RatingStars';
import { useAuth } from '../hooks/AuthContext';
import { extrairErro, reviewsApi, sessionsApi, STATUS_CORRIDA, usersApi } from '../services/api';

const formatarData = (iso) => {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleDateString('pt-BR');
  } catch {
    return '';
  }
};

const formatarValor = (v) =>
  v && Number(v) > 0 ? `R$ ${Number(v).toFixed(2).replace('.', ',')}` : 'Gratuito';

export default function PerfilScreen({ navegar }) {
  const { usuario, logout } = useAuth();

  const [perfil, setPerfil] = useState(usuario);
  const [corridas, setCorridas] = useState([]);
  const [avaliacoes, setAvaliacoes] = useState([]);
  const [aba, setAba] = useState('corridas'); // 'corridas' | 'avaliacoes'
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [erro, setErro] = useState(null);
  const [modalAvaliar, setModalAvaliar] = useState(null); // { corrida, avaliadoNome }

  /**
   * Carga inicial: busca o perfil atualizado (média, pontos), as CORRIDAS do
   * usuário e as AVALIAÇÕES que ele recebeu — tudo ao mesmo tempo.
   *
   * ANALOGIA: três SELECTs paralelos (users, sessions, reviews) sem dependência
   * entre eles — por isso Promise.all (faz "join" só no cliente).
   */
  const carregarTudo = useCallback(async () => {
    try {
      const [perfilAtual, minhasCorridas, minhasAvaliacoes] = await Promise.all([
        usersApi.perfil(usuario.id),
        sessionsApi.minhas(0, 50),
        reviewsApi.listar(0, 50),
      ]);
      setPerfil(perfilAtual);
      setCorridas(minhasCorridas);
      setAvaliacoes(minhasAvaliacoes);
      setErro(null);
    } catch (e) {
      // 401 = token expirado: manda de volta ao login (como @ExceptionHandler
      // de AuthenticationException no backend).
      if (e?.response?.status === 401) {
        logout();
        navegar(require('./LoginScreen').default);
        return;
      }
      setErro(extrairErro(e));
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, [usuario.id, logout, navegar]);

  useEffect(() => {
    carregarTudo();
  }, [carregarTudo]);

  const aoSair = () => {
    logout();
    navegar(require('./LoginScreen').default);
  };

  /**
   * Aba "Corridas": ações por status.
   * - MATCHED/ACTIVE (com chatRoomId) -> abre o chat da corrida.
   * - COMPLETED -> abre o modal de avaliação.
   * - CANCELLED -> sem ação (só exibição).
   */
  const aoAbrirCorrida = (corrida) => {
    const emAndamento =
      (corrida.status === 'MATCHED' || corrida.status === 'ACTIVE') && corrida.chatRoomId;
    if (emAndamento) {
      navegar(require('./ChatScreen').default, {
        sessionId: corrida.id,
        chatRoomId: corrida.chatRoomId,
        titulo: corrida.postTitulo,
        voltarPara: PerfilScreen,
      });
      return;
    }
    if (corrida.status === 'COMPLETED') {
      const avaliado =
        corrida.authorId === usuario.id ? corrida.helperNome : corrida.authorNome;
      setModalAvaliar({ corrida, avaliadoNome: avaliado });
    }
  };

  /**
   * Depois de avaliar, recarrega o perfil (a média de avaliações mudou no
   * banco) — igual a um "SELECT de novo" pós-INSERT.
   */
  const aposAvaliar = useCallback(async () => {
    try {
      const perfilAtual = await usersApi.perfil(usuario.id);
      setPerfil(perfilAtual);
    } catch {
      // não bloqueia o fluxo por causa do refresh do perfil
    }
  }, [usuario.id]);

  // --------------------------------------------------------------------------
  // Cabeçalho fixo: voltar + sair
  // --------------------------------------------------------------------------
  const cabecalho = (
    <View style={styles.header}>
      <Pressable onPress={() => navegar(require('./FeedScreen').default)}>
        <Text style={styles.voltar}>voltar</Text>
      </Pressable>
      <Text style={styles.headerTitulo}>Perfil</Text>
      <Pressable style={styles.botaoSair} onPress={aoSair}>
        <Text style={styles.botaoSairTexto}>Sair</Text>
      </Pressable>
    </View>
  );

  // --------------------------------------------------------------------------
  // Bloco de perfil + abas (rola junto com a lista via ListHeaderComponent)
  // --------------------------------------------------------------------------
  const perfilBloco = (
    <View>
      <View style={styles.perfilCard}>
        <Avatar nome={perfil?.nome} avatarUrl={perfil?.avatarUrl} size={84} />
        <View style={styles.perfilInfos}>
          <Text style={styles.nome}>{perfil?.nome || '—'}</Text>
          {perfil?.githubUsername ? (
            <Text style={styles.github}>@{perfil.githubUsername}</Text>
          ) : null}
          {perfil?.bio ? <Text style={styles.bio}>{perfil.bio}</Text> : null}

          {perfil?.tecnologiasDominadas?.length > 0 ? (
            <View style={styles.chips}>
              {perfil.tecnologiasDominadas.map((tech) => (
                <View key={tech} style={styles.chip}>
                  <Text style={styles.chipTexto}>{tech}</Text>
                </View>
              ))}
            </View>
          ) : null}
        </View>
      </View>

      <View style={styles.stats}>
        <View style={styles.stat}>
          <Text style={styles.statValor}>{perfil?.saldoPontos ?? 0}</Text>
          <Text style={styles.statRotulo}>pontos</Text>
        </View>
        <View style={styles.statDivisor} />
        <View style={styles.stat}>
          <Text style={styles.statValor}>
            {perfil?.mediaAvaliacoes != null
              ? Number(perfil.mediaAvaliacoes).toFixed(1).replace('.', ',')
              : '—'}
          </Text>
          <Text style={styles.statRotulo}>média</Text>
          <RatingStars nota={Number(perfil?.mediaAvaliacoes ?? 0)} tamanho="pe" />
        </View>
      </View>

      <View style={styles.abas}>
        {[
          { chave: 'corridas', rotulo: 'Corridas' },
          { chave: 'avaliacoes', rotulo: 'Avaliações' },
        ].map((tab) => (
          <Pressable
            key={tab.chave}
            style={[styles.aba, aba === tab.chave && styles.abaOn]}
            onPress={() => setAba(tab.chave)}
          >
            <Text style={[styles.abaTexto, aba === tab.chave && styles.abaTextoOn]}>
              {tab.rotulo}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );

  // --------------------------------------------------------------------------
  // Item de corrida
  // --------------------------------------------------------------------------
  const dados = aba === 'corridas' ? corridas : avaliacoes;

  const renderCorrida = ({ item }) => {
    const status = STATUS_CORRIDA[item.status] || { rotulo: item.status, cor: '#6B7280' };
    const praOnde =
      (item.status === 'MATCHED' || item.status === 'ACTIVE') && item.chatRoomId;
    const podeAvaliar = item.status === 'COMPLETED';

    return (
      <Pressable
        style={styles.corrida}
        onPress={() => aoAbrirCorrida(item)}
        disabled={!praOnde && !podeAvaliar}
      >
        <Text style={styles.corridaTitulo} numberOfLines={2}>
          {item.postTitulo}
        </Text>
        <Text style={styles.corridaMeta} numberOfLines={1}>
          {item.authorNome}
          {item.helperNome ? `  ⇄  ${item.helperNome}` : ''}
        </Text>
        <View style={styles.corridaRodape}>
          <View style={[styles.badge, { backgroundColor: `${status.cor}18` }]}>
            <Text style={[styles.badgeTexto, { color: status.cor }]}>{status.rotulo}</Text>
          </View>
          <Text style={styles.corridaMeta}>
            {formatarValor(item.recompensaValor)} · {formatarData(item.createdAt)}
          </Text>
          {praOnde && <Text style={styles.acoes}>Abrir chat →</Text>}
          {podeAvaliar && <Text style={styles.acoes}>Avaliar ★</Text>}
        </View>
      </Pressable>
    );
  };

  const renderAvaliacao = ({ item }) => (
    <View style={styles.avaliacao}>
      <View style={styles.avaliacaoCabeca}>
        <RatingStars nota={item.nota} tamanho="pe" />
        <Text style={styles.avaliacaoNome}>{item.reviewerNome}</Text>
        <Text style={styles.avaliacaoData}>{formatarData(item.createdAt)}</Text>
      </View>
      {item.comentario ? (
        <Text style={styles.avaliacaoComentario}>{item.comentario}</Text>
      ) : null}
    </View>
  );

  const renderItem = aba === 'corridas' ? renderCorrida : renderAvaliacao;
  const chave = (item) => (aba === 'corridas' ? item.id : item.id);
  const vazio =
    aba === 'corridas'
      ? 'Você ainda não tem corridas. Aceite um socorro no feed!'
      : 'Nenhuma avaliação recebida ainda.';

  return (
    <View style={styles.container}>
      {cabecalho}

      {erro && <Text style={styles.erro}>{erro}</Text>}

      {carregando ? (
        <View style={styles.carregando}>
          <ActivityIndicator size="large" color="#7C3AED" />
        </View>
      ) : (
        <FlatList
          data={dados}
          keyExtractor={chave}
          renderItem={renderItem}
          ListHeaderComponent={perfilBloco}
          refreshControl={
            <RefreshControl
              refreshing={atualizando}
              onRefresh={() => {
                setAtualizando(true);
                carregarTudo();
              }}
            />
          }
          ListEmptyComponent={<Text style={styles.vazio}>{vazio}</Text>}
        />
      )}

      <AvaliarCorridaModal
        visivel={Boolean(modalAvaliar)}
        corrida={modalAvaliar?.corrida}
        avaliadoNome={modalAvaliar?.avaliadoNome}
        onFechar={() => setModalAvaliar(null)}
        onConcluida={aposAvaliar}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F6F0FF' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
  },
  voltar: { color: '#7C3AED', fontWeight: '700', fontSize: 15 },
  headerTitulo: { fontSize: 16, fontWeight: '800', color: '#1F2937' },
  botaoSair: {
    backgroundColor: '#FEF2F2',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  botaoSairTexto: { color: '#B91C1C', fontWeight: '700', fontSize: 13 },
  perfilCard: {
    flexDirection: 'row',
    gap: 14,
    padding: 16,
  },
  perfilInfos: { flex: 1 },
  nome: { fontSize: 20, fontWeight: '800', color: '#1F2937' },
  github: { fontSize: 14, color: '#7C3AED', fontWeight: '600', marginTop: 2 },
  bio: { fontSize: 14, color: '#4B5563', marginTop: 6 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 10 },
  chip: { backgroundColor: '#EDE9FE', borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4 },
  chipTexto: { fontSize: 12, fontWeight: '600', color: '#5B21B6' },
  stats: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    borderRadius: 12,
    paddingVertical: 12,
  },
  stat: { alignItems: 'center', flex: 1, gap: 2 },
  statDivisor: { width: 1, height: 36, backgroundColor: '#E5E7EB' },
  statValor: { fontSize: 18, fontWeight: '800', color: '#1F2937' },
  statRotulo: { fontSize: 12, color: '#6B7280' },
  abas: { flexDirection: 'row', gap: 8, paddingHorizontal: 16, paddingTop: 14, paddingBottom: 4 },
  aba: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 10,
    backgroundColor: '#F3F4F6',
    alignItems: 'center',
  },
  abaOn: { backgroundColor: '#7C3AED' },
  abaTexto: { fontWeight: '700', fontSize: 14, color: '#4B5563' },
  abaTextoOn: { color: '#FFFFFF' },
  corrida: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginTop: 10,
    borderRadius: 12,
    padding: 14,
  },
  corridaTitulo: { fontSize: 15, fontWeight: '700', color: '#1F2937' },
  corridaMeta: { fontSize: 13, color: '#6B7280', marginTop: 4 },
  corridaRodape: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginTop: 8 },
  badge: { borderRadius: 999, paddingHorizontal: 10, paddingVertical: 3 },
  badgeTexto: { fontSize: 11, fontWeight: '700' },
  acoes: { color: '#7C3AED', fontWeight: '700', fontSize: 13, marginLeft: 'auto' },
  avaliacao: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginTop: 10,
    borderRadius: 12,
    padding: 14,
  },
  avaliacaoCabeca: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
  avaliacaoNome: { fontWeight: '700', fontSize: 14, color: '#1F2937' },
  avaliacaoData: { fontSize: 12, color: '#9CA3AF', marginLeft: 'auto' },
  avaliacaoComentario: { fontSize: 14, color: '#4B5563', marginTop: 8 },
  erro: {
    backgroundColor: '#FEF2F2',
    color: '#B91C1C',
    padding: 10,
    marginHorizontal: 16,
    borderRadius: 8,
    marginTop: 8,
  },
  carregando: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  vazio: {
    textAlign: 'center',
    color: '#6B7280',
    marginTop: 32,
    paddingHorizontal: 32,
    marginBottom: 24,
  },
});