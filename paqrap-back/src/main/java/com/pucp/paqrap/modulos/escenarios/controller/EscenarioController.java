package com.pucp.paqrap.modulos.escenarios.controller;

import com.pucp.paqrap.comun.api.PaginaResponse;
import com.pucp.paqrap.modulos.escenarios.dto.CrearEscenarioRequest;
import com.pucp.paqrap.modulos.escenarios.dto.EscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.dto.ResultadoEscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.service.EscenarioService;
import com.pucp.paqrap.modulos.escenarios.service.ResultadoEscenarioCsvExporter;
import com.pucp.paqrap.modulos.escenarios.service.ScenarioResultService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/escenarios")
public class EscenarioController {

    private static final MediaType CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final EscenarioService escenarioService;
    private final ScenarioResultService resultadoService;
    private final ResultadoEscenarioCsvExporter csvExporter;

    public EscenarioController(EscenarioService escenarioService, ScenarioResultService resultadoService,
                               ResultadoEscenarioCsvExporter csvExporter) {
        this.escenarioService = escenarioService;
        this.resultadoService = resultadoService;
        this.csvExporter = csvExporter;
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

    /** Resultado final persistido de una ejecución, disponible solo cuando existe su consolidado. */
    @GetMapping("/{id}/resultado")
    public ResultadoEscenarioResponse resultado(@PathVariable long id) {
        return resultadoService.consultarResultado(id);
    }

    /** Misma representación consolidada del resultado, serializada como una sola fila CSV. */
    @GetMapping(value = "/{id}/resultado/csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportarResultadoCsv(@PathVariable long id) {
        ResultadoEscenarioResponse resultado = resultadoService.consultarResultado(id);
        return ResponseEntity.ok()
                .contentType(CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resultado-escenario-" + id + ".csv\"")
                .body(csvExporter.exportar(resultado).getBytes(StandardCharsets.UTF_8));
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
