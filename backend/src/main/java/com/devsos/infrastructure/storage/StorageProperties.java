package com.devsos.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do object storage (S3-compatível).
 *
 * <p>O DevSOS usa a API S3 (AWS SDK v2) contra o <b>MinIO</b> local em
 * desenvolvimento — um "S3 de mentira" que roda num container Docker e usa o
 * MESMO protocolo do S3 real. Com isso:
 * <ul>
 *   <li>você pratica o SDK antes de pagar conta na AWS;</li>
 *   <li>trocar para AWS S3 / Cloudflare R2 / DigitalOcean Spaces depois é só
 *       mudar o {@code endpoint} (+ credenciais) — nenhum código muda.</li>
 * </ul>
 *
 * <p>Propriedades (variáveis de ambiente SEM dev: prefixo {@code DEV_SOS_...}):
 * <pre>{@code
 * devsos.storage.endpoint   = DEV_SOS_STORAGE_ENDPOINT   (ex.: http://localhost:9000)
 * devsos.storage.access-key = DEV_SOS_STORAGE_ACCESS_KEY
 * devsos.storage.secret-key = DEV_SOS_STORAGE_SECRET_KEY
 * devsos.storage.bucket     = DEV_SOS_STORAGE_BUCKET     (padrão: devsos)
 * devsos.storage.region     = DEV_SOS_STORAGE_REGION     (padrão: us-east-1)
 * }</pre>
 * Com {@code endpoint} vazio o storage fica <b>desligado</b> (upload responde
 * 503) — é o comportamento padrão se ninguém configurar nada.
 */
@ConfigurationProperties(prefix = "devsos.storage")
public record StorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    String region,
    /** Tipos MIME aceitos no upload (imagens de print do problema). */
    java.util.Map<String, String> contentTypes
) {

    public StorageProperties {
        if (bucket == null || bucket.isBlank()) bucket = "devsos";
        if (region == null || region.isBlank()) region = "us-east-1";
        if (contentTypes == null || contentTypes.isEmpty()) {
            contentTypes = java.util.Map.of(
                "image/png", "png",
                "image/jpeg", "jpg",
                "image/webp", "webp",
                "image/gif", "gif"
            );
        }
    }

    /** Storage configurado? (endpoint definido = cliente S3 existe) */
    public boolean estaHabilitado() {
        return endpoint != null && !endpoint.isBlank()
            && accessKey != null && !accessKey.isBlank()
            && secretKey != null && !secretKey.isBlank();
    }
}