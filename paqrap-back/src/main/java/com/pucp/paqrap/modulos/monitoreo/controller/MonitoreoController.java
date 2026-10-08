package com.pucp.paqrap.modulos.monitoreo.controller;

import com.pucp.paqrap.modulos.monitoreo.dto.MonitoreoResponse;
import com.pucp.paqrap.modulos.monitoreo.service.MonitoreoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoreo")
public class MonitoreoController {

    private final MonitoreoService monitoreoService;

    public MonitoreoController(MonitoreoService monitoreoService) {
        this.monitoreoService = monitoreoService;
    }

    /** Estado del mapa e inventarios de una ejecución; el front lo consulta periódicamente. */
    @GetMapping("/{ejecucionId}")
    public MonitoreoResponse estado(@PathVariable long ejecucionId) {
        return monitoreoService.estado(ejecucionId);
    }
}
