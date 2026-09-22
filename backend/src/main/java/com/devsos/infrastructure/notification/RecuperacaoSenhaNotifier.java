package com.devsos.infrastructure.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "Envio" do token de recuperação de senha.
 *
 * <h2>Fase 1: sem provedor de e-mail</h2>
 * <p>Ainda não há SMTP/serviço de e-mail integrado. Nesta fase o token é
 * <b>registrado no log</b> da aplicação (e, se
 * {@code devsos.auth.expor-token-reset=true}, também devolvido na resposta de
 * {@code /forgot-password} para DEV/TESTE).</p>
 *
 * <h2>Próximo passo</h2>
 * <p>Quando entrar um provedor (SMTP, SES, Resend…), basta trocar o corpo de
 * {@link #enviar} por um envio real — o resto do fluxo não muda. O link usa
 * {@code devsos.auth.reset-link-base} (ex.: a tela de reset do cliente web).</p>
 */
@Component
public class RecuperacaoSenhaNotifier {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaNotifier.class);

    private final String linkBase;

    public RecuperacaoSenhaNotifier(@Value("${devsos.auth.reset-link-base:}") String linkBase) {
        this.linkBase = linkBase;
    }

    public void enviar(String email, String tokenCru) {
        String link = (linkBase == null || linkBase.isBlank())
            ? "(configure devsos.auth.reset-link-base para montar o link)"
            : linkBase + "?token=" + tokenCru;
        log.warn("[RECUPERACAO-SENHA] fase 1 (sem provedor de e-mail) | para={} | token={} | link={}",
            email, tokenCru, link);
    }
}
