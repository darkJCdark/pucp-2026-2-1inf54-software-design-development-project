package com.pucp.paqrap.modulos.flota.controller;

import com.pucp.paqrap.modulos.flota.dto.MantenimientoRequest;
import com.pucp.paqrap.modulos.flota.dto.MantenimientoResponse;
import com.pucp.paqrap.modulos.flota.service.MantenimientoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/flota/mantenimientos")
public class MantenimientoController {

    private final MantenimientoService mantenimientoService;

    public MantenimientoController(MantenimientoService mantenimientoService) {
        this.mantenimientoService = mantenimientoService;
    }

    @GetMapping
    public List<MantenimientoResponse> listar(
            @RequestParam(required = false) String vehiculoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return mantenimientoService.listar(vehiculoId, desde, hasta);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MantenimientoResponse programar(@Valid @RequestBody MantenimientoRequest request) {
        return mantenimientoService.programar(request);
    }

    @DeleteMapping("/{vehiculoId}/{fecha}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable String vehiculoId,
                         @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        mantenimientoService.cancelar(vehiculoId, fecha);
    }
}
