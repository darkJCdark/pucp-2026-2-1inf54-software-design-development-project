package com.pucp.paqrap.modulos.flota.controller;

import com.pucp.paqrap.modulos.flota.dto.ActualizarTipoVehiculoRequest;
import com.pucp.paqrap.modulos.flota.dto.CambiarEstadoRequest;
import com.pucp.paqrap.modulos.flota.dto.TipoVehiculoResponse;
import com.pucp.paqrap.modulos.flota.dto.VehiculoResponse;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.service.FlotaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/flota")
public class FlotaController {

    private final FlotaService flotaService;

    public FlotaController(FlotaService flotaService) {
        this.flotaService = flotaService;
    }

    @GetMapping("/tipos")
    public List<TipoVehiculoResponse> listarTipos() {
        return flotaService.listarTipos();
    }

    @GetMapping("/tipos/{tipo}")
    public TipoVehiculoResponse obtenerTipo(@PathVariable VehicleType tipo) {
        return flotaService.obtenerTipo(tipo);
    }

    @PutMapping("/tipos/{tipo}")
    public TipoVehiculoResponse actualizarTipo(@PathVariable VehicleType tipo,
                                               @Valid @RequestBody ActualizarTipoVehiculoRequest request) {
        return flotaService.actualizarTipo(tipo, request);
    }

    @GetMapping("/vehiculos")
    public List<VehiculoResponse> listarVehiculos(@RequestParam(required = false) VehicleType tipo,
                                                  @RequestParam(required = false) VehicleOperationalStatus estado) {
        return flotaService.listarVehiculos(tipo, estado);
    }

    @GetMapping("/vehiculos/{id}")
    public VehiculoResponse obtenerVehiculo(@PathVariable String id) {
        return flotaService.obtenerVehiculo(id);
    }

    @PatchMapping("/vehiculos/{id}/estado")
    public VehiculoResponse cambiarEstado(@PathVariable String id, @Valid @RequestBody CambiarEstadoRequest request) {
        return flotaService.cambiarEstado(id, request.estado());
    }
}
