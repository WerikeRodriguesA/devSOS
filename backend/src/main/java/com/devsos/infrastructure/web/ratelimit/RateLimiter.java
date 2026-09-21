package com.devsos.infrastructure.web.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limitador de requisições em memória, thread-safe, por chave (ex.: IP+rota).
 *
 * <h2>Algoritmo: janela fixa com cooldown progressivo</h2>
 * <p>Cada chave tem uma janela de tempo e um contador. Enquanto o contador não
 * passa do limite, a requisição é permitida. Ao estourar, a chave fica
 * <b>bloqueada</b> por um cooldown que <b>dobra a cada novo estouro</b>
 * (limitado a {@code maxCooldownSegundos}) — é o "cooldown progressivo": quem
 * insiste demora cada vez mais para voltar a tentar. Um período longo sem
 * estourar zera os strikes.</p>
 *
 * <h2>Por que não bucket4j?</h2>
 * <p>A issue sugeria bucket4j OU um filtro próprio com cache local. Optamos
 * pelo filtro próprio: zero dependência nova, algoritmo transparente e
 * testável — e o comportamento que queremos (janela + backoff) cabe em poucas
 * linhas. Se um dia precisarmos de limites distribuídos (várias instâncias),
 * aí sim vale uma solução com estado compartilhado (Redis/bucket4j).</p>
 *
 * <h2>Memória</h2>
 * <p>Sem limpeza, um atacante variando IPs faria o mapa crescer sem limite.
 * A cada {@value #LIMPEZA_A_CADA} chamadas varremos e removemos chaves ociosas
 * (sem bloqueio ativo e paradas há mais de {@value #TTL_MS} ms).</p>
 */
public final class RateLimiter {

    private static final long TTL_MS = 10 * 60_000L;
    private static final int LIMPEZA_A_CADA = 1024;
    private static final int MAX_STRIKES = 10;

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxCooldownSegundos;
    private final AtomicLong chamadas = new AtomicLong();

    public RateLimiter(Clock clock, int maxCooldownSegundos) {
        this.clock = clock;
        this.maxCooldownSegundos = Math.max(1, maxCooldownSegundos);
    }

    /**
     * Registra uma tentativa para {@code chave} e diz se pode passar.
     *
     * @return {@link Decisao} com {@code permitido}, o quanto resta na janela
     *         e, quando bloqueado, quantos segundos esperar ({@code Retry-After}).
     */
    public Decisao tentar(String chave, int limite, int janelaSegundos) {
        long agora = clock.millis();
        long janelaMs = janelaSegundos * 1000L;
        Decisao[] decisao = new Decisao[1];

        // compute() é atômico POR CHAVE: duas requisições do mesmo IP não
        // perdem incremento (o clássico race de "leu 4, os dois gravaram 5").
        estados.compute(chave, (k, atual) -> {
            Estado e = atual != null ? atual : new Estado(agora);

            // Bloqueado: devolve o tempo restante sem mexer no contador.
            if (agora < e.bloqueadoAte) {
                decisao[0] = new Decisao(false, 0, segundosAte(e.bloqueadoAte, agora));
                return e;
            }

            // Janela expirou: começa uma nova.
            if (agora - e.janelaInicio >= janelaMs) {
                e.janelaInicio = agora;
                e.contador = 0;
                e.bloqueadoAte = 0;
                // strikes só decaem após um bom comportamento prolongado.
                if (e.ultimoEstouro > 0 && agora - e.ultimoEstouro > TTL_MS) {
                    e.strikes = 0;
                    e.ultimoEstouro = 0;
                }
            }

            e.contador++;
            if (e.contador > limite) {
                e.strikes++;
                e.ultimoEstouro = agora;
                long cooldown = Math.min(
                    janelaSegundos * (1L << Math.min(e.strikes - 1, MAX_STRIKES)),
                    maxCooldownSegundos);
                e.bloqueadoAte = agora + cooldown * 1000L;
                e.janelaInicio = agora;
                e.contador = 0;
                decisao[0] = new Decisao(false, 0, cooldown);
                return e;
            }

            decisao[0] = new Decisao(true, limite - e.contador, 0);
            return e;
        });

        if (chamadas.incrementAndGet() % LIMPEZA_A_CADA == 0) {
            limpar(agora);
        }
        return decisao[0];
    }

    /** Quantas chaves estão sendo rastreadas agora (útil em teste/diagnóstico). */
    public int chavesRastreadas() {
        return estados.size();
    }

    private void limpar(long agora) {
        estados.entrySet().removeIf(entrada -> {
            Estado e = entrada.getValue();
            long ultimaAtividade = Math.max(e.janelaInicio, e.ultimoEstouro);
            return e.bloqueadoAte < agora && (agora - ultimaAtividade) > TTL_MS;
        });
    }

    private static long segundosAte(long fim, long agora) {
        long ms = fim - agora;
        return Math.max(1, (ms + 999) / 1000);
    }

    private static final class Estado {
        long janelaInicio;
        int contador;
        int strikes;
        long bloqueadoAte;
        long ultimoEstouro;

        Estado(long agora) {
            this.janelaInicio = agora;
        }
    }

    /** Resultado de {@link #tentar}: passou? quanto resta? quanto esperar? */
    public record Decisao(boolean permitido, int restante, long retryAfterSegundos) {
    }
}
