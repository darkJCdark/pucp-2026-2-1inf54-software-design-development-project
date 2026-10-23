package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.incidencias.service.ContextoSimulacion;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Expone al registro de averías el reloj y el plan en ejecución de los escenarios activos. */
@Component
public class ContextoSimulacionEscenarios implements ContextoSimulacion {

    private final MotorEscenarios motor;
    private final EjecutorPlanService ejecutorPlan;

    public ContextoSimulacionEscenarios(MotorEscenarios motor, EjecutorPlanService ejecutorPlan) {
        this.motor = motor;
        this.ejecutorPlan = ejecutorPlan;
    }

    @Override
    public Optional<Instant> instanteSimulado(long ejecucionId) {
        return motor.instanteSimulado(ejecucionId);
    }

    @Override
    public Optional<Instant> ejecutadoHasta(long ejecucionId) {
        return motor.ejecutadoHasta(ejecucionId);
    }

    @Override
    public Optional<Location> posicionEstimada(long ejecucionId, String vehiculoId, Instant instante) {
        return ejecutorPlan.posicionEstimada(ejecucionId, vehiculoId, instante);
    }
}
