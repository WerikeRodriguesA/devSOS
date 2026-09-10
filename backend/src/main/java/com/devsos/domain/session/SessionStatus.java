package com.devsos.domain.session;

/**
 * Ciclo de vida de uma "corrida" (a chamada de ajuda estilo Uber).
 *
 * <pre>
 *   MATCHED ──▶ ACTIVE ──▶ COMPLETED
 *      └─── ▶ CANCELLED   (post volta para OPEN)
 * </pre>
 *
 * <ul>
 *   <li><b>MATCHED</b> — o helper "aceitou o socorro"; o post sai do feed
 *       (vira IN_PROGRESS) para ninguém mais aceitar.</li>
 *   <li><b>ACTIVE</b> — corrida em andamento (a sala de chat está ativa).</li>
 *   <li><b>COMPLETED</b> — resolução entregue; os pontos do post
 *       ({@code recompensa_valor}) são transferidos ao helper.</li>
 *   <li><b>CANCELLED</b> — não rolou; o post volta a ficar aberto.</li>
 * </ul>
 */
public enum SessionStatus {
    MATCHED,
    ACTIVE,
    COMPLETED,
    CANCELLED
}