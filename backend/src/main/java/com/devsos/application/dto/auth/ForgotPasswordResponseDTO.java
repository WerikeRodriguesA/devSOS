package com.devsos.application.dto.auth;

/**
 * Resposta de {@code POST /api/auth/forgot-password}.
 *
 * <p>{@code message} é sempre a mesma (não revela se o e-mail existe).
 * {@code tokenDesenvolvimento} só vem preenchido quando
 * {@code devsos.auth.expor-token-reset=true} — um facilitador de DEV/TESTE
 * enquanto não há provedor de e-mail (em produção fica {@code null}).</p>
 */
public record ForgotPasswordResponseDTO(String message, String tokenDesenvolvimento) {
}
