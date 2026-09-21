package com.devsos.infrastructure.storage;

import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.application.exception.ResourceNotFoundException;
import com.devsos.application.exception.StorageIndisponivelException;
import com.devsos.application.exception.TipoDeArquivoNaoSuportadoException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Porta para o object storage S3-compatível (MinIO em dev, S3 real em prod).
 *
 * <p>Os dois métodos que importam: {@link #salvar} (grava bytes) e
 * {@link #buscar} (lê de volta). O nome da chave é gerado AQUI, nunca vem do
 * cliente — o que evita path traversal e colisão de nomes.</p>
 *
 * <p><b>Segurança da extensão:</b> a extensão do arquivo é decidida pelo
 * <b>content-type</b> declarado no upload (que só aceita imagens da lista),
 * NÃO pelo nome original do arquivo. Oras: se você confiasse no
 * {@code foto.png}, bastaria renomear {@code malware.exe} para
 * {@code malware.png} e servir um executável como imagem.</p>
 */
@Service
public class StorageService {

    /** Chaves que nós mesmos geramos: UUID v4 + extensão da lista. */
    private static final Pattern PADRAO_CHAVE = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp|gif)$");

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final StorageProperties props;

    public StorageService(ObjectProvider<S3Client> s3ClientProvider, StorageProperties props) {
        this.s3ClientProvider = s3ClientProvider;
        this.props = props;
    }

    /** Grava bytes no bucket e devolve a chave do objeto (para virar URL). */
    public String salvar(byte[] bytes, String contentType) {
        String extensao = contentType == null ? null : props.contentTypes().get(contentType);
        if (extensao == null) {
            String aceitos = String.join(", ", props.contentTypes().keySet());
            throw new TipoDeArquivoNaoSuportadoException(
                "Formato não suportado. Envie uma imagem: " + aceitos + ".");
        }

        String chave = UUID.randomUUID() + "." + extensao;
        try {
            s3().putObject(PutObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(chave)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .build(),
                RequestBody.fromBytes(bytes));
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageIndisponivelException("Falha ao gravar a imagem no storage.", ex);
        }
        return chave;
    }

    /** Lê os bytes de uma chave (para o GET público servir a imagem). */
    public StoredObject buscar(String chave) {
        if (chave == null || !PADRAO_CHAVE.matcher(chave).matches()) {
            throw new RegraDeNegocioException("Chave de imagem inválida.");
        }
        try {
            ResponseBytes<GetObjectResponse> resposta =
                s3().getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(chave)
                    .build());
            return new StoredObject(resposta.asByteArray(), resposta.response().contentType());
        } catch (NoSuchKeyException ex) {
            throw new ResourceNotFoundException("Imagem não encontrada.");
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageIndisponivelException("Falha ao ler a imagem do storage.", ex);
        }
    }

    /** Idempotente: garante que o bucket existe (chamado no boot). */
    public void criarBucketSeNecessario() {
        try {
            s3().headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
        } catch (NoSuchBucketException ex) {
            s3().createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
        }
    }

    private S3Client s3() {
        S3Client cliente = s3ClientProvider.getIfAvailable();
        if (cliente == null) {
            throw new StorageIndisponivelException(
                "Storage não está configurado. Defina DEV_SOS_STORAGE_ENDPOINT (ex.: http://localhost:9000 do MinIO).");
        }
        return cliente;
    }

    /** Bytes + content-type de um objeto lido do bucket. */
    public record StoredObject(byte[] conteudo, String contentType) {
    }
}