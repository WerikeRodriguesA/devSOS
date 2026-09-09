# devSOS — Como o aplicativo guarda suas informações (versão sem "tech")

> Este documento é para todos: empresários, designers, testadores e curiosos.
> Aqui não existe nenhuma palavra técnica complicada. Prometido.

---

## 1. O que é este documento?

Quando você usa um celular ou um site, as informações precisam morar em algum
lugar. Esse "lugar" é um **banco de dados** — imagine um **arquivo com várias
gavetas**. O devSOS usa quatro gavetas. Este documento explica o que tem dentro
de cada uma delas, do jeito mais simples possível.

---

## 2. As quatro gavetas (tabelas)

### Gaveta 1 — "Cadastro dos Devs" (usuários)

Quem entra no devSOS tem uma ficha aqui. É como o **cadastro da academia**:
nome, e-mail, o seu usuário do GitHub e uma foto (avatar). Também ficam aqui
duas coisas importantes:

- **Pontos**: cada vez que um dev ajuda alguém, ganha pontos. Como milhas de
  companhia aérea.
- **Nota média**: quanto os outros devs avaliam o trabalho dele, de 1 a 5.
  É igual à avaliação de motorista no Uber **ao contrário** (aqui o especialista
  é avaliado, não o passageiro). Se ele tem 4,9 estrelas, é gente boa.

Com exceção da foto, todas as gavetas usam essa regra: **ninguém pode se
cadastrar duas vezes com o mesmo e-mail ou o mesmo GitHub** — igual CPF no
cadastro de banco.

### Gaveta 2 — "Mural de Recados" (posts)

É o nosso **feed**, o mesmo estilo do Instagram. Cada publicação do problema é
um "bilhete pregado no mural":

- O **título** e a **descrição** do bug (ex.: "Minha API trava quando recebe
  JSON vazio").
- O **print** do erro (`media`).
- **Etiquetas** (`tags`), tipo "Java", "Spring", "PostgreSQL" — como adesivos
  coloridos no bilhete, para facilitar achar problemas parecidos.
- Se a ajuda é **de graça (FREE)** ou **paga (PAID com recompensa)**.
  É a regra do pão: graça não pode cobrar; pago tem que ter preço.
- O **estado** do bilhete:
  - **ABERTO** → ainda ninguém atendeu.
  - **EM ANDAMENTO** → já tem um especialista mexendo.
  - **RESOLVIDO** → problema foi solucionado.
  - **CANCELADO** → o autor desistiu ou removeu.

### Gaveta 3 — "O Chaveiro" (sessions — as "corridas")

Quando dois computadores travam na fila do banco, o "chaveiro" é a peça que
liga eles. No devSOS, essa gaveta é a **central de chamadas**, igual ao sistema
do Uber que liga motorista e passageiro. Aqui são guardadas:

- Qual problema (post) está envolvido;
- Qual especialista (helper) pegou;
- O número da **sala de chat** (`chat_room_id`) onde os dois conversam;
- O estado da "corrida": que chegou, em atendimento, concluída ou cancelada.

### Gaveta 4 — "Caderninho de Avaliações" (reviews)

Depois que a ajuda termina, o autor e o especialista se avaliam. É o mesmo
princípio do Uber: **motorista avalia passageiro e passageiro avalia
motorista**. As regras desse caderninho:

- A nota é só de **1 a 5** (não dá para dar 7);
- **Cada pessoa só avalia uma vez** por atendimento (não dá para "inflar"
  a nota);
- **Ninguém avalia a si mesmo** (óbvio, mas agora ninguém pode trapacear);
- A nota média da Gaveta 1 é atualizada **sozinha** toda vez que alguém avalia.
  É como uma planilha que soma e divide sozinha.

---

## 3. Como as gavetas se conversam?

Elas se referenciam entre si por **números de série únicos**. Exemplo real:

> A Ana (cadastro) publica um bug (mural). O Bruno vê, clica em "Aceitar
> Socorro" e o sistema cria uma **corrida** (chaveiro) ligando Ana ↔ Bruno com
> uma sala de chat. No final, a Ana dá 5 estrelas para o Bruno (caderninho),
> e a nota média do Bruno sobe sozinha.

Se alguém apagar o cadastro da Ana, o sistema apaga junto os bilhetes dela,
qualquer "corrida" dela e quaisquer avaliações relacionadas — é a regra do
**efeito dominó organizado**: nada de informação órfã voando solta.

---

## 4. Como o app impede que dois especialistas peguem a mesma dúvida?

Esse é o problema do **"pega-pega"**: dois devs vendo o mesmo bug e os dois
clicando "Aceitar" exatamente no mesmo instante.

O app usa um truque de **semáforo** — igual o semáforo do trânsito:

1. Quando um especialista clica em "Aceitar", o sistema **fecha a vez** dele:
   só ele pode continuar com aquele problema nesse momento.
2. O sistema verifica: "esse post ainda está ABERTO?".
   - Se **está**: ele ganha a corrida e o post vira **EM ANDAMENTO**.
   - Se **já não está** (outro pegou primeiro): o sistema responde com carinho
     "Ops, alguém já aceitou esse socorro. Olha estes outros?".
3. **Trava extra no banco de dados:** além do semáforo, o banco tem uma regra
   que proíbe matematicamente existirem dois "em atendimento" para o mesmo
   post — mesmo que os dois cliques aconteçam num intervalo de milissegundos.

**Resultado:** em qualquer cenário, só um especialista "dirige" cada chamado.
Impossível dois devs pegarem o mesmo problema ao mesmo tempo.

---

## 5. "O que acontece se eu apagar X?"

| Se você apagar... | O que acontece com o resto? |
|-------------------|-----------------------------|
| Um cadastro de dev | Somem os bilhetes dele, as corridas dele e as avaliações dele. |
| Um bilhete (post)  | Somem as corridas e avaliações daquele bilhete. |
| Uma corrida        | Somem apenas as avaliações daquela corrida. |
| Um usuário que recebeu avaliações | Ele sai do sistema com tudo, para não deixar "fantasmas". |

Depois pensamos em "apagar com calma" (guardar dados em arquivo-morto) para
auditorias — típico de produto maduro.

---

## 6. Perguntas frequentes (sem complicação)

**"Meus pontos podem ficar negativos?"**
Não. Pontos negativos seriam como dívida de ficha na lanchonete — o sistema
simplesmente não deixa.

**"Posso avaliar alguém mais de uma vez no mesmo atendimento?"**
Não. Sempre há uma votação por atendimento por pessoa. Justo, né?

**"Se eu apagar meu cadastro, meu histórico de avaliações some?"**
Sim, junto. Depois, com o tempo, dá para mudar para "arquivar" em vez de
apagar — deixa o histórico.

**"Essas notas são de verdade?"**
Sim, e além disso o sistema **atualiza a nota da pessoa sozinho** — ninguém
precisa ficar conferindo na mão.

---

*Este documento acompanha a parte técnica (`docs/TECNICA.md`). Quando o
aplicativo crescer, estas gavetas podem ganhar mais cadernos — como as
mensagens do chat, que hoje ficam guardadas em outro lugar (em tempo real).*