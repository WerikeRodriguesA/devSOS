package com.devsos.application.exception;

/**
 * Exceção de domínio: um recurso não existe (você pediu um usuário ou post
 * com UUID não encontrado).
 * <p>
 * DIDÁTICA: prefira criar uma exceção do DOMÍNIO do app (ex.: "usuário não
 * encontrado") a lançar exceções genéricas ou expor exceções de biblioteca
 * (ex.: {@code NoSuchElementException} do Java). Assim o código que consome
 * a API e o handler de erro sabem EXATAMENTE o quê aconteceu e podem traduzir
 * para HTTP 404 com uma mensagem amigável.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensagem) {
        super(mensagem);
    }

    /**
     * Fábrica que monta mensagens padrão ("Post não encontrado") a partir do
     * nome do recurso — evita repetir texto string em cada Service.
     */
    public static ResourceNotFoundException of(String recurso) {
        return new ResourceNotFoundException(String.format("%s não encontrado(a)", recurso));
    }
}