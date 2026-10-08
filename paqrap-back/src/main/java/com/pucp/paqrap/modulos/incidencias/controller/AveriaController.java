package com.pucp.paqrap.modulos.incidencias.controller;

import com.pucp.paqrap.modulos.incidencias.dto.AveriaResponse;
import com.pucp.paqrap.modulos.incidencias.dto.RegistrarAveriaRequest;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.incidencias.service.AveriaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/incidencias/averias")
public class AveriaController {

    private final AveriaService averiaService;

    public AveriaController(AveriaService averiaService) {
        this.averiaService = averiaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AveriaResponse registrar(@Valid @RequestBody RegistrarAveriaRequest request) {
        return averiaService.registrar(request);
    }

    @GetMapping
    public List<AveriaResponse> listar(
            @RequestParam(required = false) String vehiculoId,
            @RequestParam(required = false) BreakdownType tipo,
            @RequestParam(required = false) Long ejecucionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {
        return averiaService.listar(vehiculoId, tipo, ejecucionId, desde, hasta);
    }

    @GetMapping("/{id}")
    public AveriaResponse obtener(@PathVariable Long id) {
        return averiaService.obtener(id);
    }
}
