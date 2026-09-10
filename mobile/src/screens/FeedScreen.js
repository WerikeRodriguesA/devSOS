/**
 * screens/FeedScreen.js
 * ---------------------------------------------------------------------------
 * O feed: lista os posts OPEN vindo do backend (GET /api/posts) e permite
 * "Aceitar Socorro" (POST /api/sessions).
 *
 * CONCEITOS EXPLICADOS COM ANALOGIA DE BACKEND:
 * - useState: cada item de estado É uma "variável observada". No backend, é
 *   como ter um atributo + um listener que re-renderiza a tela ao mudar
 *   (equivalente a um bean com propriedade observada pelo frontend).
 * - useEffect com []: roda UMA VEZ quando a tela "monta" — como um
 *   @EventListener(ApplicationReadyEvent) ou a leitura inicial do banco.
 * - FlatList: é a "paginação lazy" do React. Enquanto ScrollView monta TUDO
 *   de uma vez (como SELECT sem LIMIT), a FlatList só renderiza os itens
 *   visíveis — igual a um query com páginas sob demanda.
 * - RefreshControl: recarrega como um "carregar dados do repositório" manual.
 */

import { useCallback, useEffect, useState } from 'react';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import PostCard from '../components/PostCard';
import { extrairErro, feedApi, sessionsApi } from '../services/api';

export default function FeedScreen({ navegar }) {
  const [posts, setPosts] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [aceitandoId, setAceitandoId] = useState(null);
  const [erro, setErro] = useState(null);

  /**
   * useEffect([], dependencies = []) -> roda uma vez ao montar.
   *
   * ANALOGIA: método init() da tela — busca o feed do "repositório".
   * Como vem de uma chamada assíncrona, precisamos de async/await e de
   * atualizar o estado ao final (sucesso E falha).
   */
  const buscarFeed = useCallback(async () => {
    try {
      const dados = await feedApi.buscar(0, 20);
      setPosts(dados);
      setErro(null);
    } catch (e) {
      setErro(extrairErro(e));
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  useEffect(() => {
    buscarFeed();
  }, [buscarFeed]);

  /**
   * "Aceitar Socorro" -> POST /api/sessions { postId }.
   * Retorna a corrida com chatRoomId; embaralhamos direto para o chat.
   */
  const aceitarSocorro = async (post) => {
    setAceitandoId(post.id);
    try {
      const corrida = await sessionsApi.aceitarSocorro(post.id);
      navegar(require('./ChatScreen').default, {
        sessionId: corrida.id,
        chatRoomId: corrida.chatRoomId,
        titulo: corrida.postTitulo,
      });
    } catch (e) {
      setErro(extrairErro(e));
    } finally {
      setAceitandoId(null);
    }
  };

  return (
    <View style={styles.container}>
      {/* Cabeçalho */}
      <View style={styles.header}>
        <Text style={styles.headerTitulo}>Feed</Text>
        <View style={styles.headerAcoes}>
          <Pressable
            style={styles.botaoPerfil}
            onPress={() => navegar(require('./PerfilScreen').default)}
          >
            <Text style={styles.botaoPerfilTexto}>Perfil</Text>
          </Pressable>
          <Pressable
            style={styles.botaoNovo}
            onPress={() => navegar(require('./CreatePostScreen').default)}
          >
            <Text style={styles.botaoNovoTexto}>+ Postar</Text>
          </Pressable>
        </View>
      </View>

      {/* Estado de erro (não derruba a tela — só mostra o problema) */}
      {erro && (
        <Text style={styles.erro} onPress={buscarFeed}>
          {erro} (toque para tentar de novo)
        </Text>
      )}

      <FlatList
        data={posts}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <PostCard
            post={item}
            aceitando={aceitandoId === item.id}
            onAceitar={aceitarSocorro}
          />
        )}
        refreshControl={
          <RefreshControl
            refreshing={atualizando}
            onRefresh={() => {
              setAtualizando(true);
              buscarFeed();
            }}
          />
        }
        ListEmptyComponent={
          carregando ? (
            <Text style={styles.vazio}>Carregando posts...</Text>
          ) : (
            <Text style={styles.vazio}>
              Nenhum post aguardando ajuda por enquanto.
            </Text>
          )
        }
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
    paddingTop: 12,
    paddingBottom: 8,
  },
  headerTitulo: { fontSize: 24, fontWeight: '800', color: '#4B3869' },
  headerAcoes: { flexDirection: 'row', gap: 8 },
  botaoPerfil: {
    borderWidth: 1,
    borderColor: '#7C3AED',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  botaoPerfilTexto: { color: '#7C3AED', fontWeight: '700', fontSize: 14 },
  botaoNovo: {
    backgroundColor: '#7C3AED',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  botaoNovoTexto: { color: '#FFFFFF', fontWeight: '700', fontSize: 14 },
  erro: {
    backgroundColor: '#FEF2F2',
    color: '#B91C1C',
    padding: 10,
    marginHorizontal: 16,
    borderRadius: 8,
    marginBottom: 4,
  },
  vazio: {
    textAlign: 'center',
    color: '#6B7280',
    marginTop: 48,
  },
});