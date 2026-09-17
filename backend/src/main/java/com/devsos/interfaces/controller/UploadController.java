package com.devsos.interfaces.controller;

import com.devsos.application.dto.upload.UploadResponseDTO;
import com.devsos.infrastructure.storage.StorageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;

/**
 * Upload/serviço de imagens (prints do problema).
 *
 * <p><b>POST /api/uploads</b> (exige JWT): recebe um arquivo {@code multipart}
 * e devolve {@code {"mediaUrl": "http://host/api/uploads/{chave}"}}. Quem
 * criar, pode passar essa URL no {@code mediaUrl} do post.</p>
 *
 * <p><b>GET /api/uploads/{chave}</b> (público): serve o arquivo direto do
 * object storage com as respostas HTTP certinhas (Content-Type, cache).</p>
 *
 * <p><b>Por que o GET proxy no backend?</b> Se a URL apontasse direto pro
 * MinIO ({@code localhost:9000}), o navegador do Web Client quebraria o CORS
 * pra renderizar a imagem, e o MinIO ficaria exposto. Servir pelo backend
 * mantém um único domínio — como os CDNs de produção fazem.</p>
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * POST /api/uploads — envia a imagem (multipart/form-data, campo
     * {@code arquivo}). Exige JWT (identificado pelo Authorize no Swagger).
     * 201 com {@code mediaUrl}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponseDTO> enviar(
            @RequestPart("arquivo") MultipartFile arquivo) {
        try {
            String chave = storageService.salvar(arquivo.getBytes(), arquivo.getContentType());
            String mediaUrl = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/uploads/")
                .path(chave)
                .toUriString();

            return ResponseEntity.status(HttpStatus.CREATED).body(new UploadResponseDTO(mediaUrl));
        } catch (java.io.IOException ex) {
            throw new com.devsos.application.exception.StorageIndisponivelException(
                "Não foi possível ler o arquivo enviado.", ex);
        }
    }

    /**
     * GET /api/uploads/{chave} — público, serve a imagem com cache de 30 dias
     * (o conteúdo é determinístico: mesmo arquivo → mesmo bytes).
     */
    @SecurityRequirements(value = {}) // imagem é pública (como o feed)
    @GetMapping("/{chave}")
    public ResponseEntity<byte[]> baixar(@PathVariable String chave) {
        StorageService.StoredObject objeto = storageService.buscar(chave);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(objeto.contentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
            .body(objeto.conteudo());
    }
}