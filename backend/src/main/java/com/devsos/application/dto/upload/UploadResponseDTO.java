package com.devsos.application.dto.upload;

/**
 * Resposta de um upload de imagem.
 *
 * <p>{@code mediaUrl} aponta para um endpoint do PRÓPRIO backend
 * ({@code GET /api/uploads/{chave}}), que serve o arquivo a partir do
 * storage. Isso mantém um único domínio (sem CORS de imagem) e esconde o
 * MinIO/S3 do mundo — trocar de storage depois não muda a URL.</p>
 */
public record UploadResponseDTO(String mediaUrl) {
}