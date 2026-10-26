package com.pucp.paqrap.modulos.flota.controller;

import com.pucp.paqrap.modulos.flota.dto.AltaVehiculoRequest;
import com.pucp.paqrap.modulos.flota.dto.VehiculoResponse;
import com.pucp.paqrap.modulos.flota.service.FlotaCantidadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Alta y baja de unidades de la flota (cantidad por tipo). La consulta sigue en {@code FlotaController}. */
@RestController
@RequestMapping("/api/flota/vehiculos")
public class FlotaCantidadController {

    private final FlotaCantidadService cantidadService;

    public FlotaCantidadController(FlotaCantidadService cantidadService) {
        this.cantidadService = cantidadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehiculoResponse alta(@Valid @RequestBody AltaVehiculoRequest request) {
        return cantidadService.alta(request.tipo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void baja(@PathVariable String id) {
        cantidadService.baja(id);
    }
}
