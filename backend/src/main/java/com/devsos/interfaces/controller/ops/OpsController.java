package com.devsos.interfaces.controller.ops;

import com.devsos.application.service.ObsService;
import com.devsos.infrastructure.observability.MetricasDeSOS;
import com.devsos.infrastructure.observability.MetricasDeSOS.SnapshotDeMetricas;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devsos.application.service.ObsService.HealthDeSOS;

/**
 * Rotas de operacao do DevSOS (issue #18 - Fase 1, offline first).
 *
 * <ul>
 *   <li>{@code GET /api/ops/health} - PUBLICO (probe L7 do orquestrador): status
 *       geral, banco e jwt, ou 503 se algum componente critico estiver DOWN.</li>
 *   <li>{@code GET /api/ops/metrics} - AUTENTICADO (JWT): snapshot das metricas
 *       de negocio (corridas por status, tempo do aceite, falhas).</li>
 * </ul>
 *
 * <p>Contrato JSON: snake_case, mesmo padrao do resto da API (docs/API.md).
 * Quando a rede voltar o follow-up muda o backend para Actuator/obslit pero
 * mantem este endpoint e este JSON (roadmap docs/API.md #18).</p>
 */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final ObsService obsService;
    private final MetricasDeSOS metricas;

    public OpsController(ObsService obsService, MetricasDeSOS metricas) {
        this.obsService = obsService;
        this.metricas = metricas;
    }

    /** Health publico: 200 se geral=UP, 503 se banco ou jwt caiu. */
    @GetMapping("/health")
    public ResponseEntity<HealthDeSOS> health() {
        HealthDeSOS health = obsService.health();
        boolean up = "UP".equals(health.status()) && "UP".equals(health.banco());
        return up
            ? ResponseEntity.ok(health)
            : ResponseEntity.status(503).body(health);
    }

    /** Metricas (exige JWT - SecurityConfig protege /api/ops/metrics). */
    @GetMapping("/metrics")
    public SnapshotDeMetricas metrics() {
        return metricas.snapshot();
    }
}
