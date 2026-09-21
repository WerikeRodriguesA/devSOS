package com.devsos.infrastructure.web.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Clock controlável para testar janelas/cooldowns sem esperar em tempo real. */
final class RelogioFake extends Clock {

    private Instant agora;

    RelogioFake(Instant inicio) {
        this.agora = inicio;
    }

    void avancar(Duration duracao) {
        this.agora = this.agora.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
