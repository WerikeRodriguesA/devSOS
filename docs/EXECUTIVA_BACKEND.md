# DevSOS — O que o app faz (versão para leigos)

> Este documento explica — sem nenhum termo técnico — o que a "Temporada 1"
> do DevSOS faz: mostrar o feed de dúvidas e o perfil dos devs.

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

## 5. O que vem depois

Esta etapa cobre **perfis e o feed**. No futuro:

- O botão "Aceitar Socorro" (criar a "corrida"/sala de chat);
- Login com senha/GitHub (hoje o autor é informado no pedido);
- Upload da foto do erro direto do celular.

---

*Este documento acompanha os detalhes técnicos em `docs/API.md` e
`docs/TECNICA.md`.*