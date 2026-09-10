package com.devsos.application.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de CADASTRO ({@code POST /api/auth/register}).
 *
 * <h2>Nota didática sobre validação</h2>
 * As regras de formulário (campos obrigatórios, tamanhos) ficam AQUI, no DTO
 * de entrada. O front recebe os erros agrupados por campo ({@code fieldErrors})
 * e o Service recebe apenas dados JÁ validados — uma fronteira clara.
 */
public record RegistroRequestDTO(

    @NotBlank(message = "Informe seu nome")
    @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres")
    String nome,

    @NotBlank(message = "Informe seu e-mail")
    @Email(message = "E-mail inválido")
    @Size(max = 255, message = "E-mail muito longo")
    String email,

    @NotBlank(message = "Informe uma senha")
    @Size(min = 8, max = 100, message = "A senha deve ter entre 8 e 100 caracteres")
    String senha,

    @Size(max = 60, message = "GitHub username muito longo")
    String githubUsername
) {}