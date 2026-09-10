# DevSOS — O que o app faz (versão para leigos)

> Este documento explica — sem nenhum termo técnico — o que a "Temporada 1"
> do DevSOS faz: mostrar o feed de dúvidas, o perfil dos devs, a conta/login
> e a **corrida de socorro** (pedir ajuda, ser aceito e se avaliar).

---

## 1. A analogia do restaurante

Para entender o código que a gente escreveu, imagine um **restaurante**:

| No restaurante… | No DevSOS… | O que faz |
|-----------------|------------|-----------|
| **Garçom** anota o pedido | O **Controller** | Recebe o pedido do cliente (o app), confere se está bem escrito e leva para a cozinha |
| **Chef de cozinha** prepara o prato | O **Service** | Aplica as regras ("bife mal passado não pode virar bem passado") e monta o prato |
| **Estoquista** busca ingrediente | O **Repository** | Busca e guarda as informações no estoque (o banco de dados) |
| **O prato que o cliente recebe** | O **DTO** | O que sai da cozinha é uma *apresentação* bonita, nunca a panela inteira |

O **garçom nunca entra na despensa** (o Controller não toca no banco) e o
**chef nunca vai atender a mesa** (o Service não devolve HTML/JSON direto).
Cada um na sua função — é assim que um código fica fácil de dar manutenção.

---

## 2. Entendendo o que você, usuário, pode fazer hoje

### Ver o perfil de um dev

Quando você abre o perfil de alguém (o "Junior" que acabou de se cadastrar),
o app mostra: nome, foto, e-mail, o GitHub dele, quantos **pontos** ele juntou,
a **nota média** que os outros devs dão e as **tecnologias** que ele diz
dominar (Java, Spring, etc.).

O botão de "tecnologias" é editável: se o dev aprendeu Docker, ele atualiza a
lista. Simples como editar a lista de contatos favoritos do celular.

### Abrir o feed de dúvidas

O app consulta o mural e devolve **as dúvidas ainda sem dono** (status
"aberto"): título, descrição do bug, print, etiquetas (ex.: "java", "docker"),
e quem postou. Ele mostra **poucos por vez** (paginação), do mais novo para o
mais antigo — igual rolar o Instagram.

### Publicar uma dúvida

O dev escreve o problema (título + descrição + print + etiquetas), diz se a
ajuda é **gratuita** ou **paga** e clica em publicar.

Aqui valem duas regrinhas do nosso salão:

- **Grátis não cobra.** Você não pode marcar "gratuito" e colocar um valor.
- **Pago cobra.** Se você marcou "pago", precisa dizer quanto.

Se alguém tentar furadar essas regras, o app responde com uma mensagem clara
("Opa, post gratuito não pode ter recompensa").

### E se algo der errado?

Tudo que dá errado retorna sempre **no mesmo formato**, com uma mensagem que
faz sentido. Exemplos:

- Tentou abrir um perfil que não existe → "Usuário não encontrado".
- Esqueceu de preencher o título → o app aponta exatamente qual campo e o que
  está errado, como um formulário do Detran.
- Digitar um endereço que não existe → "Rota não encontrada".

Ou seja: o app **nunca** mostra aquela tela cinza de "deu pau". Ele sempre
explica o que aconteceu do seu jeito.

---

## 3. O "detetive" dos erros (responsabilidade extra do restaurante)

Tem um funcionário especial chamado **GlobalExceptionHandler** — o "supervisor
de reclamações". Quando qualquer pedido dá errado em qualquer mesa, ele assume
e devolve uma resposta padronizada. Um empregado só, que conhece todas as
regras de erro — em vez de cada garçom inventar a própria desculpa.

---

## 4. Como os dados são protegidos

- Você **nunca** manda (nem recebe) o "cadastro completo" do banco pelo app:
  o sistema faz uma **cópia de exibição** (o DTO) — só o necessário.
- Campos que você não deve controlar (quando o post foi criado, se ele está
  "aberto" ou "em andamento") são definidos **pelo sistema**, não pelo app.
- As datas ficam sempre no padrão UTC internacional — mesmo que o dev esteja
  no Brasil e o usuário na Índia, todo mundo vê o mesmo horário "certinho".

---

## 5. Conta e identidade (login)

A nova "temporada" adicionou **criar conta e entrar** — o DevSOS agora sabe
**quem é você**:

- **Criar conta:** informa nome, e-mail e senha. A senha **nunca é guardada
  em texto puro** (nem o próprio sistema consegue ler de volta) — é como
  guardar um cofre com a chave quebrada: quem roubar o arquivo não usa.
- **Entrar:** e-mail + senha. Se acertar, o sistema entrega um **crachá
  digital** (um "token") com validade de 1 hora.
- **Usar o crachá:** o app anexa o crachá em cada pedido protegido. Se o
  crachá estiver velho (mais de 1 hora) ou falso, o sistema responde
  "não autenticado" (código 401).
- **E-mail já cadastrado?** O sistema avisa na hora — ninguém duplica conta.

### O que isso mudou para você, usuário

- **O autor do post agora é você de verdade.** Antes o dev dizia "sou o Fulano"
  e o sistema acreditava. Agora o autor sai da conta logada — é impossível
  publicar como outra pessoa.
- **Update de perfil é seu:** só quem está logado altera as próprias
  tecnologias. Ninguém edita o perfil de terceiros.
- **O feed continua aberto:** ver posts e perfis não exige cadastro (como o
  Instagram).

> **Crachá com prazo de validade?** Não se preocupe: o 1h é renovado
> automaticamente a cada novo login. Refresh automático entra na próxima etapa.

---

## 6. A "corrida" de socorro (aceitar, atender e avaliar)

A parte mais legal do DevSOS: quando um dev publica uma dúvida no feed, outro
dev pode **aceitar o socorro** — e aí nasce uma **corrida**, quase um pedido de
Uber entre desenvolvedores.

### O ciclo da corrida, sem termos técnicos

1. **Alguém pede ajuda** (o post entra no feed, aguardando um voluntário).
2. **Um dev aceita** — na hora, o post **sai do feed** (ninguém mais pode
   pegar) e a corrida ganha uma **sala de chat** própria, onde os dois vão
   conversar ao vivo.
3. **Os dois conversam e resolvem.** Um deles marca o início do atendimento.

### A conversa ao vivo (o chat)

Quando os dois devs estão na sala, eles conversam **em tempo real** — igual a
uma **ligação telefônica**, não a uma troca de cartas. Numa carta (como são as
outras telas do app), você pergunta, o correio entrega, e a resposta volta um
instagram depois. No chat, as palavras saem da boca de um e caem na tela do
outro **na hora**, sem o outro precisar ficar apertando F5.

Tecnicamente, o chat usa uma **"frequência de rádio" por sala**: quem está na
sala fica **sintonizado** no canal dela (o app chama isso de *tema da sala*) e
só ouve o que é falado ali — e só quem está na corrida consegue sintonizar. Por
isso dois chats diferentes não se misturam, e um estranho que tente entrar ou
falar recebe o aviso padrão de sempre ("você não participa desta sala").

> Quando alguém sai da tela e volta, a conversa não se perde: o app guarda
> **cada frase dita** em um caderninho próprio (o "histórico"). O tempo real
> mostra o que está acontecendo agora; o histórico mostra o que já foi dito —
> os dois juntos é que formam o chat completo.
4. **O atendimento termina**: quem ajudou marca como **concluído**.
   - Se a ajuda era **paga**, os **pontos do orador são transferidos** para o
     dev que ajudou — automaticamente, como um Pix. Se o orador não tem saldo,
     o sistema avisa antes e não deixa concluir.
5. **Cada um avalia o outro** (nota de 1 a 5 + comentário). A **nota média**
   de cada dev é recalculada na hora — dá para ver no perfil.
6. **Cancelamento**: a qualquer momento (antes de concluir) alguém pode
   **desistir** — o post volta ao feed e outro dev pode tentar.

### As regrinhas do salão (que o sistema garante sozinho)

- **Só uma corrida por post** — o post some do feed enquanto está em
  atendimento (e, se dois devs clicarem "aceitar" no mesmo instante, o sistema
  deixa só um passar).
- **Ninguém aceita o próprio pedido** — o sistema barra (mesmo se o app
  tentar "falar por fora").
- **Só quem está na corrida enxerga a corrida** — terceiros nem sabem que ela
  existe.
- **Só conclui quem ajudou** — o dono do post não pode marcar "pronto" no
  lugar do voluntário.
- **Só avalia depois de terminar** — e cada um avalia **uma única vez** por
  corrida, sempre o **outro lado** (ninguém escolhe avaliar estranhos).
- **Post pago exige saldo** — se o orador não tem pontos para pagar a
  recompensa, a conclusão é bloqueada.

> Assim como nas outras telas, quando algum pedido quebra uma dessas regras o
> app responde **na mesma linguagem de sempre** (o "supervisor de
> reclamações" padroniza tudo), explicando o que aconteceu.

---

## 7. O que vem depois

Esta etapa cobre **perfis, feed, autenticação e a corrida de socorro**.
No futuro:

- Renovação automática do token (refresh) e "sair de todos os aparelhos";
- Upload da foto do erro direto do celular.

---

*Este documento acompanha os detalhes técnicos em `docs/API.md` e
`docs/TECNICA.md`.*