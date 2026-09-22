package com.devsos.application.service;

import com.devsos.infrastructure.observability.MetricasDeSOS;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Health do DevSOS (issue #18 - offline first).
 *
 * <p>Usa {JdbcTemplate} com SELECT 1 (o mesmo probe que orquestrador usa):
 * zero dependencia nova, 100% offline. O JWT vai a DOWN quando as falhas
 * recentes passam da tolerancia em {MetricasDeSOS#snapshot()}.</p>
 *
 * <p>Follow-up (roadmap docs/API.md #18): Actuator/Prometheus quando a rede
 * voltar, mantendo o mesmo contrato JSON em /api/ops/health.</p>
 */
@Service
public class ObsService {

    static final long TOLERANCIA_FALHAS_JWT = 100;

    private final JdbcTemplate jdbcTemplate;
    private final MetricasDeSOS metricas;

    public ObsService(JdbcTemplate jdbcTemplate, MetricasDeSOS metricas) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricas = metricas;
    }

    /** Snapshot de health: status geral, banco, jwt e timestamp. */
    public HealthDeSOS health() {
        boolean bancoOk = bancoResponde();
        long falhasJwt = metricas.snapshot().falhasJwt();
        String banco = bancoOk ? "UP" : "DOWN";
        String jwt = falhasJwt <= TOLERANCIA_FALHAS_JWT ? "UP" : "DOWN";
        String geral = bancoOk ? "UP" : "DOWN";
        return new HealthDeSOS(geral, banco, jwt, OffsetDateTime.now());
    }

    /** SELECT 1: ponto classico de probe de banco (K8s/health). */
    private boolean bancoResponde() {
        try {
            Integer um = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return um != null && um == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Contrato JSON imutavel do health (snake_case). */
    public record HealthDeSOS(String status, String banco, String jwt, OffsetDateTime data) {}
}
