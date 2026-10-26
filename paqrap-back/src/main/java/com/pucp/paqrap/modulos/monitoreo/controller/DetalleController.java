package com.pucp.paqrap.modulos.monitoreo.controller;

import com.pucp.paqrap.modulos.monitoreo.dto.AlmacenDetalleResponse;
import com.pucp.paqrap.modulos.monitoreo.dto.VehiculoDetalleResponse;
import com.pucp.paqrap.modulos.monitoreo.service.DetalleOperacionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Detalle de unidades (P-05) y almacenes (P-07) para los paneles del mapa; ambos requieren la ejecución. */
@RestController
@RequestMapping("/api")
public class DetalleController {

    private final DetalleOperacionService detalleService;

    public DetalleController(DetalleOperacionService detalleService) {
        this.detalleService = detalleService;
    }

    @GetMapping("/flota/vehiculos/{id}/detalle")
    public VehiculoDetalleResponse vehiculo(@PathVariable String id, @RequestParam long ejecucionId) {
        return detalleService.vehiculo(id, ejecucionId);
    }

    /** {@code horas}: ventana del historial y del consumo, de 1 a 72 (24 por defecto). */
    @GetMapping("/almacenes/{id}/detalle")
    public AlmacenDetalleResponse almacen(@PathVariable String id, @RequestParam long ejecucionId,
                                          @RequestParam(defaultValue = "24") int horas) {
        return detalleService.almacen(id, ejecucionId, horas);
    }
}
