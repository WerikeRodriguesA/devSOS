package com.devsos.application.exception;

/**
 * Storage não está disponível (não configurado via {@code DEV_SOS_STORAGE_*}
 * ou o MinIO/S3 deu erro de conexão). Respondido como <b>503 Service
 * Unavailable</b> — o serviço existe, mas seu recurso externo está fora.
 */
public class StorageIndisponivelException extends RuntimeException {

    public StorageIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public StorageIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}