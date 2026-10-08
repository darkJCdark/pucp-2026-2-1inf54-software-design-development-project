package com.pucp.paqrap.modulos.escenarios.controller;

import com.pucp.paqrap.comun.api.PaginaResponse;
import com.pucp.paqrap.modulos.escenarios.dto.CrearEscenarioRequest;
import com.pucp.paqrap.modulos.escenarios.dto.EscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.service.EscenarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/escenarios")
public class EscenarioController {

    private final EscenarioService escenarioService;

    public EscenarioController(EscenarioService escenarioService) {
        this.escenarioService = escenarioService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EscenarioResponse crear(@Valid @RequestBody CrearEscenarioRequest request) {
        return escenarioService.crear(request);
    }

    /** Ejecuciones de la más reciente a la más antigua. */
    @GetMapping
    public PaginaResponse<EscenarioResponse> listar(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return escenarioService.listar(page, size);
    }

    @GetMapping("/{id}")
    public EscenarioResponse obtener(@PathVariable long id) {
        return escenarioService.obtener(id);
    }

    @PostMapping("/{id}/iniciar")
    public EscenarioResponse iniciar(@PathVariable long id) {
        return escenarioService.iniciar(id);
    }

    @PostMapping("/{id}/pausar")
    public EscenarioResponse pausar(@PathVariable long id) {
        return escenarioService.pausar(id);
    }

    @PostMapping("/{id}/reanudar")
    public EscenarioResponse reanudar(@PathVariable long id) {
        return escenarioService.reanudar(id);
    }

    @PostMapping("/{id}/detener")
    public EscenarioResponse detener(@PathVariable long id) {
        return escenarioService.detener(id);
    }
}
