package com.devsos.infrastructure.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Cria o cliente S3 quando o storage está configurado.
 *
 * <p>O {@code @ConditionalOnExpression} só instancia o bean quando a variável
 * de ambiente realmente aponta um endpoint não vazio — se ela não existir (ou
 * vier vazia) a aplicação sobe sem storage (upload responde 503), útil para
 * quem não quiser rodar o MinIO. (Não usamos {@code @ConditionalOnProperty}
 * porque a propriedade SEMPRE existe — de clara no application.properties com
 * default vazio.)</p>
 *
 * <p><b>{@code forcePathStyle(true)} — a pegadinha clássica do MinIO:</b> por
 * padrão o SDK do S3 monta URLs no formato "virtual-hosted" (bucket → subdomínio:
 * {@code devsos.localhost:9000}); o MinIO não aceita isso, só o estilo "path"
 * ({@code localhost:9000/devsos/...}). Buckets públicos como este NUNCA usam
 * subdomínio real, então sempre forçamos path-style.</p>
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnExpression("'${devsos.storage.endpoint:}' != ''")
    public S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
            .endpointOverride(URI.create(props.endpoint()))
            .region(Region.of(props.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
            .forcePathStyle(true)
            .build();
    }
}