package com.devsos.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/auth/change-password} (autenticado): exige a
 * senha ATUAL correta antes de trocar.
 */
public record ChangePasswordRequestDTO(

    @NotBlank(message = "Informe a senha atual")
    String senhaAtual,

    @NotBlank(message = "Informe a nova senha")
    @Size(min = 8, max = 100, message = "A senha deve ter entre 8 e 100 caracteres")
    String novaSenha
) {
}
