package com.devsos.infrastructure.security;

import java.security.Principal;
import java.util.UUID;

/**
 * Identidade de um usuário dentro de uma conexão WebSocket (STOMP).
 *
 * <p>O Spring WebSocket também trabalha com o conceito de {@link Principal}
 * ("quem está logado"), assim como o REST. Para não trazer a entidade do banco
 * nem refazer a cadeia completa do {@code UserDetails}, criamos um record enxuto
 * que carrega só o que o chat precisa: o {@code userId} extraído do JWT.</p>
 *
 * <p><b>Por que implementar {@link Principal}?</b> Porque o STOMP propaga o
 * principal pela sessão e permite o {@code @MessageMapping} receber o usuário
 * logado por parâmetro (padrão da plataforma, sem gambiarras).</p>
 */
public record ChatPrincipal(UUID userId) implements Principal {

    /**
     * O "nome" do principal no STOMP é o id do usuário (texto).
     * É por ele que os interceptores e controllers recuperam quem falou.
     */
    @Override
    public String getName() {
        return userId.toString();
    }
}