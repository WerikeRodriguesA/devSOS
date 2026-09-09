package com.devsos.application.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TRATAMENTO GLOBAL DE ERROS.
 *
 * <h2>O que é {@code @RestControllerAdvice}?</h2>
 * Um "cinto de segurança" centralizado: qualquer exceção lançada por qualquer
 * Controller passa por aqui antes de virar resposta HTTP. Sem essa classe,
 * cada Controller teria que ter seus próprios {@code try/catch} — código
 * duplicado e fácil de esquecer.
 *
 * <h2>Como funciona o {@code @ExceptionHandler}?</h2>
 * Dizemos "quando acontecer a exceção X, execute este método". O Spring
 * invoca o handler correspondente e o valor de retorno (nosso {@link ApiError})
 * é serializado como JSON pelo próprio Spring.
 *
 * <p>Ordem de resolução: o Spring escolhe o handler MAIS ESPECÍFICO para a
 * exceção lançada. Ex.: {@code DataIntegrityViolationException} é mãe de
 * quase todos os erros de SQL — se um dia precisarmos de regra fina,
 * criamos handlers filhos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404 — recurso de domínio não existe (ResourceNotFoundException). */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        var error = ApiError.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /** 400 — regra de negócio violada (ex.: post FREE com recompensa). */
    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ApiError> handleRegraDeNegocio(RegraDeNegocioException ex) {
        var error = ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage()
        );
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * 400 — falha ao validar {@code @NotBlank/@Size/@NotNull} de um RequestDTO.
     * A exceção lançada pelo {@code @Valid} do Controller é a
     * {@code MethodArgumentNotValidException}.
     * <p>
     * Repare que agrupamos as mensagens por CAMPO ({@code fieldErrors}).
     * Isso permite que o front mostre o erro embaixo de cada input.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        var error = ApiError.withFields(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Requisição inválida. Verifique os campos informados.",
            fieldErrors
        );
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * 400 — validações de parâmetros não vinculados a um DTO (ex.: UUID
     * inválido no path, parâmetro faltando). Ex.: {@code GET /api/users/abc}
     * em que "abc" não é UUID.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        var error = ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage()
        );
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * 400 — JSON mal formado (ex.: chave a mais lá no lado do cliente) ou tipo
     * incompatível. Ajusta a mensagem para ser amigável.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        var error = ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Corpo da requisição inválido ou JSON mal formado."
        );
        return ResponseEntity.badRequest().body(error);
    }

    /** 400 — tipo do parâmetro inválido (ex.: page=abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        var error = ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Parâmetro inválido: " + ex.getName()
        );
        return ResponseEntity.badRequest().body(error);
    }

    /** 400 — parâmetro obrigatório ausente (ex.: faltou ?page=...). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        var error = ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Parâmetro obrigatório ausente: " + ex.getParameterName()
        );
        return ResponseEntity.badRequest().body(error);
    }

    /** 404 — rota inexistente para GET. Evita resposta padrão do Tomcat. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        var error = ApiError.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            "Rota não encontrada: " + ex.getResourcePath()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * 500 — qualquer exceção não prevista. Caímos aqui por último
     * (o handler mais genérico). Nunca exponha o stack trace ao cliente:
     * devolvemos mensagem neutra e logamos o detalhe no console.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        // No mínimo registramos no log — essencial para investigar o 500.
        // Em produção, use um logger (Slf4j) em vez de System.out.
        System.err.println("[ERRO NÃO TRATADO] " + ex.getMessage());
        ex.printStackTrace(System.err);

        var error = ApiError.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "Erro interno no servidor. Tente novamente mais tarde."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}