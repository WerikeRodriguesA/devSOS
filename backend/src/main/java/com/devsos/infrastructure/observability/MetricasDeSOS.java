package com.devsos.infrastructure.observability;

import com.devsos.domain.session.SessionStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metricas de negocio do DevSOS (issue #18 - Fase 1, 100% offline).
 *
 * <p>Somente java.util.concurrent (LongAdder + ConcurrentHashMap), que ja faz
 * parte do JDK: ZERO dependencia nova. Sem Actuator, sem Micrometer, sem
 * Prometheus - nenhum deles existe no .m2 offline.</p>
 *
 * <p>Metricas de negocio que a issue pede:</p>
 * <ul>
 *   <li>Corridas por status (SessionStatus: MATCHED, ACTIVE, COMPLETED, CANCELLED);</li>
 *   <li>Tempo do aceite (helper aceita o socorro) - count/total/media/min/max
 *       + histograma por faixa (&lt;1s, 1-5s, 5-30s, &gt;30s);</li>
 *   <li>Falhas de autenticacao JWT;</li>
 *   <li>HTTP 4xx e 5xx (erros do cliente e do servidor).</li>
 * </ul>
 *
 * <p>Follow-up documentado no roadmap (docs/API.md #18): migrar para o
 * MeterRegistry do Micrometer (Actuator/Prometheus) quando a rede voltar,
 * mantendo o MESMO contrato JSON de /api/ops/metrics.</p>
 */
@Component
public class MetricasDeSOS {

    private final Map<SessionStatus, LongAdder> corridasPorStatus = new ConcurrentHashMap<>();

    private final LongAdder aceiteCount = new LongAdder();
    private final LongAdder aceiteTotalMs = new LongAdder();
    private final LongAdder aceiteMinMs = new LongAdder();
    private final LongAdder aceiteMaxMs = new LongAdder();
    private final Map<String, LongAdder> aceitePorFaixa = new ConcurrentHashMap<>();

    private final LongAdder falhasJwt = new LongAdder();
    private final LongAdder http4xx = new LongAdder();
    private final LongAdder http5xx = new LongAdder();

    /** Marca o minimo como "vazio" ate o primeiro aceite. */
    private boolean temAceite = false;

    public void registrarCorridaPorStatus(SessionStatus status) {
        corridasPorStatus.computeIfAbsent(status, k -> new LongAdder()).increment();
    }

    public void registrarAceite(long duracaoMs) {
        aceiteCount.increment();
        aceiteTotalMs.add(duracaoMs);

        synchronized (this) {
            if (!temAceite) {
                aceiteMinMs.add(duracaoMs);
                aceiteMaxMs.add(duracaoMs);
                temAceite = true;
            } else {
                if (aceiteMinMs.sum() > duracaoMs) {
                    aceiteMinMs.reset();
                    aceiteMinMs.add(duracaoMs);
                }
                if (aceiteMaxMs.sum() < duracaoMs) {
                    aceiteMaxMs.reset();
                    aceiteMaxMs.add(duracaoMs);
                }
            }
        }
        aceitePorFaixa.computeIfAbsent(faixaDe(duracaoMs), k -> new LongAdder()).increment();
    }

    public void registrarFalhaJwt() {
        falhasJwt.increment();
    }

    public void registrarHttp4xx() {
        http4xx.increment();
    }

    public void registrarHttp5xx() {
        http5xx.increment();
    }

    /** Snapshot imutavel das metricas para serializar em JSON (ops/metrics). */
    public SnapshotDeMetricas snapshot() {
        Map<String, Long> corridas = new ConcurrentHashMap<>();
        corridasPorStatus.forEach((k, v) -> corridas.put(k.name(), v.sum()));

        Map<String, Long> faixas = new ConcurrentHashMap<>();
        aceitePorFaixa.forEach((k, v) -> faixas.put(k, v.sum()));

        long count = aceiteCount.sum();
        long total = aceiteTotalMs.sum();
        double media = count > 0 ? (double) total / count : 0.0;
        long min = temAceite ? aceiteMinMs.sum() : 0;
        long max = temAceite ? aceiteMaxMs.sum() : 0;

        return new SnapshotDeMetricas(
            corridas,
            new AceiteMetrics(count, total, media, min, max, faixas),
            falhasJwt.sum(),
            http4xx.sum(),
            http5xx.sum());
    }

    private static String faixaDe(long ms) {
        if (ms < 1000) return "menos-de-1s";
        if (ms < 5000) return "1s-a-5s";
        if (ms < 30000) return "5s-a-30s";
        return "mais-de-30s";
    }

    /** DTO imutavel: corridas por status + resumo do aceite + falhas + http. */
    public record SnapshotDeMetricas(
            Map<String, Long> corridasPorStatus,
            AceiteMetrics aceite,
            long falhasJwt,
            long http4xx,
            long http5xx) {}

    /** Resumo do tempo de aceite (total/media/min/max + histograma). */
    public record AceiteMetrics(
            long count,
            long totalMs,
            double mediaMs,
            long minMs,
            long maxMs,
            Map<String, Long> porFaixa) {}
}