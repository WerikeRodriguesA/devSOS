package com.devsos.application.dto.post;

import com.devsos.domain.post.PostStatus;
import com.devsos.domain.post.PostTipo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Resposta padrão de uma publicação. É um "DTO de Response".
 * <p>
 * Repare que aqui NÃO existe {@code author_id} + {@code author} como entidade:
 * o front recebe {@code autorNome}, {@code autorAvatarUrl} e os demais campos
 * "achatados" — tudo pronto para renderizar. Isso é o contrato público da API;
 * a entidade JPA é um detalhe interno.
 * <p>
 * Usamos {@code record} do Java (imutável) para DTOs: menos código, sem setters.
 */
public record PostResponseDTO(
    java.util.UUID id,
    String titulo,
    String descricao,
    String mediaUrl,
    List<String> tags,
    PostTipo tipo,
    BigDecimal recompensaValor,
    PostStatus status,
    String autorNome,
    String autorAvatarUrl,
    Instant createdAt
) {

    /**
     * Fábrica de montagem a partir da entidade. Mantém a lógica de conversão
     * perto do DTO (e não espalhada nos Services).
     */
    public static PostResponseDTO from(com.devsos.domain.post.PostEntity entity) {
        return new PostResponseDTO(
            entity.getId(),
            entity.getTitulo(),
            entity.getDescricao(),
            entity.getMediaUrl(),
            entity.getTags(),
            entity.getTipo(),
            entity.getRecompensaValor(),
            entity.getStatus(),
            entity.getAuthor().getNome(),
            entity.getAuthor().getAvatarUrl(),
            entity.getCreatedAt()
        );
    }
}