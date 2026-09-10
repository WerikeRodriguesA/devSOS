package com.devsos.application.dto.post;

import com.devsos.domain.post.PostTipo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Dados recebidos do cliente para CRIAR uma publicação. É um "DTO de Request".
 *
 * <h2>POR QUE usar um DTO separado da Entidade?</h2>
 * <ol>
 *   <li><b>Segurança:</b> se o cliente mandasse um JSON direto para
 *       {@code PostEntity}, ele poderia preencher {@code id}, {@code author},
 *       {@code status} e {@code createdAt} que ele não deveria controlar —
 *       um atacante "injetaria" um post IN_PROGRESS com id escolhido.</li>
 *   <li><b>Contrato estável:</b> o cliente envia o que o MVP aceita
 *       ({@code titulo}, {@code descricao}...) e recebe o que o MVP devolve;
 *       você pode mudar o banco sem quebrar a API.</li>
 *   <li><b>Validação:</b> as anotações do Jakarta Bean Validation
 *       ({@code @NotBlank}, {@code @Size}...) são declaradas aqui, na "porta de
 *       entrada", e o Spring as dispara sozinho no Controller.</li>
 * </ol>
 *
 * <p><b>Onde está o autor ({@code authorId})?</b> Desde a iteração de
 * autenticação, o autor é definido pelo TOKEN JWT (usuário logado), nunca pelo
 * corpo da requisição. O Controller lê o {@code IdUsuarioLogado} do
 * {@code SecurityContext} e passa para o Service.</p>
 *
 * <p><b>Observação:</b> em produção eu validaria também o corpo do POST
 * (máx. de bytes) — por enquanto fica registrado no Roadmap.
 */
public record PostCreateRequestDTO(

    @NotBlank(message = "O título é obrigatório")
    @Size(min = 3, max = 160, message = "O título deve ter entre 3 e 160 caracteres")
    String titulo,

    @NotBlank(message = "A descrição do problema é obrigatória")
    @Size(min = 10, max = 5000, message = "A descrição deve ter entre 10 e 5000 caracteres")
    String descricao,

    @Pattern(regexp = "^(https?://.*)?$", message = "A URL da imagem (print) deve começar com http:// ou https://")
    String mediaUrl,

    @Size(max = 10, message = "Você pode usar no máximo 10 tags")
    List<String> tags,

    @NotNull(message = "Informe o tipo: FREE (de graça) ou PAID (com recompensa)")
    PostTipo tipo,

    @DecimalMin(value = "0.0", message = "A recompensa não pode ser negativa")
    BigDecimal recompensaValor
) {}