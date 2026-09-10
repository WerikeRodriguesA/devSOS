package com.devsos.application.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Envelope único de erro retornado pela API — todos os endpoints de erro
 * devolvem este formato:
 * <pre>
 * {
 *   "timestamp": "2026-09-09T15:00:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "O título é obrigatório",
 *   "fieldErrors": { "titulo": "O título é obrigatório" }
 * }
 * </pre>
 * <p>
 * POR QUE uma estrutura única? O consumidor (front/mobile) pode desenhar
 * handlers genéricos: "se vier mensagem, mostre em um toast; se vier
 * fieldErrors, mostre em cada campo". Sem padrão, cada erro seria um JSON
 * diferente e o cliente teria um milhão de ifs.
 */
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    Map<String, String> fieldErrors
) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, Map.of());
    }

    public static ApiError withFields(int status, String error, String message,
                                      Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}