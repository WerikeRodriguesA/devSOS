package com.devsos.application.exception;

/**
 * Exceção de REGRA DE NEGÓCIO violada (ex.: "post PAID sem recompensa").
 * <p>
 * DIFERENÇA IMPORTANTE vs. validação de formulário: a validação de
 * formulário (DTO) usa as anotações jakarta ({@code @NotBlank}) e é disparada
 * ANTES do Service. Já esta exceção representa uma VALIDAÇÃO DE DOMÍNIO que
 * envolve lógica — como uma regra composta ("FREE não pode ter recompensa").
 * Mantê-las separadas deixa o contrato da API mais claro.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String message) {
        super(message);
    }
}