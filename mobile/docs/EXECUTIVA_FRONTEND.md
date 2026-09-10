# DevSOS — O papel do Frontend (para leigos)

## A analogia do restaurante

Imagine o DevSOS como uma grande casa de comida. Nela existem:

| Parte | Papel no restaurante | Papel no DevSOS |
|-------|----------------------|-----------------|
| **Backend (Java/Spring)** | A **cozinha** — prepara os pratos | é quem processa pedidos, valida regras, faz contas de pontos e guarda o estado das corridas |
| **Banco de dados (PostgreSQL)** | O **estoque** — os ingredientes | é onde tudo fica guardado: usuários, posts, corridas, mensagens de chat |
| **API (rotas REST + WebSocket)** | O **garçom** — leva o pedido à cozinha e traz o prato | é o balcão de pedidos: `GET /api/posts`, `POST /api/sessions`, canal de chat em `/ws-devsos` |
| **Frontend (o app mobile)** | A **sala do restaurante**: o cardápio, a mesa, o prato arrumado na sua frente | é TUDO que o usuário vê e toca: o feed, os formulários, os balões de chat |

## A metáfora fluida: você, o garçom e a cozinha

1. Você (o usuário) abre o app e vê o **cardápio** (o feed de posts) — feito
   pelo frontend.
2. Você pede um prato. O **garçom** (API) anota o pedido e grita na porta da
   cozinha: *"um post novo e uma corrida aceita!"*.
3. A **cozinha** (backend) prepara: valida, atualiza o **estoque** (banco) e
   manda o prato pronto pelo garçom de volta.
4. O **frontend** recebe o prato e o **apresenta do jeito bonito**: um card
   com tags, uma bolha de mensagem, o botão "Aceitar Socorro".

Ou seja:

> **O frontend não faz a comida e não guarda ingrediente nenhum — ele é a
> experiência de quem está na mesa.** Toda regra real (pode aceitar? pode
> ver essa corrida? a quem pagar pontos?) é decidida na **cozinha**
> (backend + banco). O app apenas mostra o resultado e repassa os pedidos.

## E o chat em tempo real?

No chat não existe "pedido e resposta" — é uma **ligação telefônica** entre
duas mesas. O frontend é o **fone em cima da mesa**:

- Você fala (envia mensagem pelo canal STOMP `/app/chat/{sala}`).
- A **central de telefonia** (o broker WebSocket do backend) conecta a sua
  mesa à mesa do outro dev.
- A mensagem aparece na tela dele **na hora** — sem ele pedir, sem apertar F5.
- Só quem está "na mesa" (na mesma corrida) escuta: a central redireciona a
  ligação, e a sala é privada.

Para isso o app usa dois "garçons" ao mesmo tempo:
- O **REST** (para o que já aconteceu: o histórico de mensagens).
- O **WebSocket** (para o que acontece agora: bolha nova em tempo real).

## O que dá para fazer hoje no app

1. **Entrar** (login) — como mostrar o crachá ao garçom.
2. **Ver o feed** — o cardápio dos problemas abertos.
3. **Postar uma dúvida** — pedir ajuda.
4. **Aceitar socorro** — virar o voluntário de uma corrida.
5. **Conversar na sala** — o chat ao vivo da corrida (texto e código).

---

# Mapa de analogias (React ↔ Backend)

Para quem vem do Java/Spring, este mapa traduz os termos do React:

| Conceito React | Como parece no backend |
|----------------|------------------------|
| `useState` | Um atributo "observado": quando muda, o Renderer (tela) roda de novo — como um bean reativo/notificador |
| `useEffect` (com `[]`) | `@PostConstruct` / `@EventListener(ApplicationReadyEvent)`: roda quando o componente "nasce" |
| `return` do `useEffect` | O `finally` / `@PreDestroy`: limpa recursos (fecha socket, cancela timer) |
| **Props** | Parâmetros de método: o pai envia dados para o filho |
| **Callback prop** (`onAceitar`) | Uma interface que o filho chama; o pai decide a ação (Inversão de Controle) |
| **Context + `useAuth`** | Bean **singleton** injetado via `@Autowired`: qualquer componente lê a sessão |
| `useRef` | Um campo mutável da classe que não dispara notificação (não "re-renderiza") |
| `FlatList` | Paginação lazy: renderiza só o que cabe na tela (como um `SELECT` com pagina) |
| `async/await` + `try/catch/finally` | `CompletableFuture.get()` + `@ExceptionHandler` de cada tela |
| `axios` + interceptors | Um `RestTemplate` com `JwtAuthenticationFilter` configurado |
| Componente (função) | Um "método que retorna tela", reutilizável — como um serviço com uma responsabilidade |
| `navegar(Tela, params)` | O "dispatcher" do Controller: `switch` de telas com payload |

Regra de ouro que usamos no projeto:

> **Componente burro, lógica na camada certa.**
> O componente só renderiza e avisa (props + callbacks). Quem chama a API o
> `services/`, quem guarda estado global é o `hooks/`, quem orquestra tela é
> a `screens/`. É o mesmo *miolo* de código que você já usa no backend:
> Controllers finos, Services com a regra, Repositories com o banco.