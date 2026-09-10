package com.devsos.application.dto.session;

import com.devsos.domain.session.SessionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Mudança de status da corrida ({@code PATCH /api/sessions/{id}}).
 * Os valores aceitos são {@code ACTIVE}, {@code COMPLETED} e {@code CANCELLED}
 * (o {@code MATCHED} é criado pelo próprio aceite).
 * <p>O Service valida a transição permitida ("máquina de estados") e quem pode
 * dispará-la — ex.: só o helper conclui, qualquer participante cancela.</p>
 */
public record SessionUpdateStatusRequestDTO(
    @NotNull(message = "Informe o novo status: ACTIVE, COMPLETED ou CANCELLED")
    SessionStatus status
) {}