package com.pucp.paqrap.modulos.incidencias.controller;

import com.pucp.paqrap.modulos.incidencias.service.ConfiguracionAveriaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/incidencias/averias/configuracion/MINOR")
public class ConfiguracionAveriaController {
    public record Actualizar(@NotNull @Positive @Digits(integer = 10, fraction = 0)
                             @DecimalMax("2147483647") BigDecimal duracionMinutos) { }
    public record Respuesta(int duracionMinutos) { }
    private final ConfiguracionAveriaService service;
    public ConfiguracionAveriaController(ConfiguracionAveriaService service) { this.service = service; }
    @GetMapping
    public Respuesta obtener() { return new Respuesta(service.duracionMenorMinutos()); }
    @PutMapping
    public Respuesta actualizar(@Valid @RequestBody Actualizar request) {
        return new Respuesta(service.actualizarMenor(request.duracionMinutos().intValueExact()));
    }
}