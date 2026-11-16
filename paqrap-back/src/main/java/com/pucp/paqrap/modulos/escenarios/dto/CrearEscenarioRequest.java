package com.pucp.paqrap.modulos.escenarios.dto;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * Configura una ejecución (CU-09). {@code inicioSimulado} es obligatorio en FIVE_DAY y COLLAPSE (desde qué fecha de
 * los datos históricos se simula) y no se envía en DAY_TO_DAY, que siempre parte de la hora actual.
 *
 * <p>{@code periodosBloqueos} son los meses (AAAA-MM) cuyos archivos de bloqueos se cargaron para esta ejecución:
 * solo esos bloqueos se muestran y se planifican. Una lista vacía significa "sin bloqueos" aunque la base de datos
 * tenga bloqueos de esas fechas; si se omite, la ejecución ve todos los bloqueos guardados (comportamiento anterior).
 */
public record CrearEscenarioRequest(@NotNull ScenarioType tipo, Instant inicioSimulado,
                                    @Valid ConfiguracionSaRequest configuracionSa,
                                    List<YearMonth> periodosBloqueos) {

    public CrearEscenarioRequest(ScenarioType tipo, Instant inicioSimulado, ConfiguracionSaRequest configuracionSa) {
        this(tipo, inicioSimulado, configuracionSa, null);
    }
}
