package com.pucp.paqrap.modulos.planificacion.controller;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ReplanificacionResponse;
import com.pucp.paqrap.modulos.planificacion.service.PlanificadorSaAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planificacion")
public class PlanificacionController {

    private final PlanificadorSaAdapter adaptador;

    public PlanificacionController(PlanificadorSaAdapter adaptador) {
        this.adaptador = adaptador;
    }

    /**
     * Último plan de SA de una ejecución (rutas y paradas para el mapa): las rutas en marcha que se conservaron más
     * las nuevas. Se guarda en memoria.
     */
    @GetMapping("/{ejecucionId}/plan")
    public PlanResponse ultimoPlan(@PathVariable long ejecucionId) {
        return adaptador.ultimoPlan(ejecucionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Plan de la ejecución", ejecucionId));
    }

    /** El mismo plan con el detalle de la última replanificación: motivo, rutas conservadas y nuevas, reasignados. */
    @GetMapping("/{ejecucionId}/replanificacion")
    public ReplanificacionResponse ultimaReplanificacion(@PathVariable long ejecucionId) {
        return adaptador.ultimaReplanificacion(ejecucionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Plan de la ejecución", ejecucionId));
    }
}
