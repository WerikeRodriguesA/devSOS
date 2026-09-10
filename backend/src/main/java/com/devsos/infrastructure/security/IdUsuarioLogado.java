package com.devsos.infrastructure.security;

import java.util.UUID;

/**
 * "Quem é você?" dentro de uma requisição autenticada.
 *
 * <p>O Spring Security trabalha com um conceito chamado {@code Principal}:
 * a identidade que está logada. Nesta aplicação o principal não é um
 * {@code UserDetails} cheio de perfis — é um RECORD enxuto com o que o
 * domínio precisa: id, e-mail e nome (tudo que o JWT carrega).</p>
 *
 * <p>Por que não o próprio {@code UserEntity}? Porque o principal atravessa
 * a camada de segurança antes mesmo de tocar o banco (ele sai do token JWT).
 * Trazer a entidade aqui exigiria uma consulta por requisição e misturaria
 * domínio com infraestrutura.</p>
 */
public record IdUsuarioLogado(UUID id, String email, String nome) {}