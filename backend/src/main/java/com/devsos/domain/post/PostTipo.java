package com.devsos.domain.post;

/**
 * Tipo de recompensa de uma publicação.
 * <p>
 * PONTOS DE ATENÇÃO (didática):
 * <ul>
 *   <li>O valor persistido é o NOME da constante ({@code FREE}, {@code PAID}),
 *       garantido pelo {@code @Enumerated(EnumType.STRING)} na entidade —
 *       nunca salvamos o número ordinal, pois ele mudaria se reordenarmos
 *       as constantes.</li>
 *   <li>No banco o tipo da coluna é {@code TEXT} (veja a DDL), então o valor
 *       salvo é literalmente {@code "FREE"} ou {@code "PAID"}.</li>
 * </ul>
 */
public enum PostTipo {
    FREE,
    PAID
}