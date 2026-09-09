package com.devsos.application.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request para ATUALIZAR as tecnologias dominadas de um usuário.
 * <p>
 * Campos com NULL são permitidos pela validação (o cliente pode não enviar um
 * campo que não quer alterar); quando o campo é optativo, a validação de
 * tamanho precisa ser feita com o operador {code @Size(max=...)} sem
 * {@code min}, já que {@code min=1} em null seria ignorado.
 */
public record AtualizarTecnologiasRequestDTO(

    @NotNull(message = "A lista de tecnologias não pode ser nula")
    @Size(max = 30, message = "O perfil suporta no máximo 30 tecnologias")
    List<@NotNull(message = "Cada tecnologia precisa de um nome") String> tecnologiasDominadas
) {}