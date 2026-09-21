package com.devsos.application.exception;

/**
 * O content-type enviado no upload não está na lista de imagens aceitas.
 * Respondido como <b>415 Unsupported Media Type</b> — "você mandou um formato
 * que esta API não sabe representar".
 */
public class TipoDeArquivoNaoSuportadoException extends RuntimeException {

    public TipoDeArquivoNaoSuportadoException(String mensagem) {
        super(mensagem);
    }
}