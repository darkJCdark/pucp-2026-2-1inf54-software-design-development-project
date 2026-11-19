package com.pucp.paqrap.modulos.redvial.controller;

import com.pucp.paqrap.modulos.redvial.dto.BloqueoResponse;
import com.pucp.paqrap.modulos.redvial.dto.CargaBloqueosResponse;
import com.pucp.paqrap.modulos.redvial.dto.CargaBloqueosMultipleResponse;
import com.pucp.paqrap.modulos.redvial.service.BloqueoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/red-vial/bloqueos")
public class BloqueoController {

    private final BloqueoService bloqueoService;

    public BloqueoController(BloqueoService bloqueoService) {
        this.bloqueoService = bloqueoService;
    }

    /** Recibe un archivo bloqueo.AAMM.txt en el campo multipart {@code archivo}. */
    @PostMapping(path = "/carga", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CargaBloqueosResponse cargar(@RequestPart("archivo") MultipartFile archivo,
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo)
            throws IOException {
        if (archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        String contenido = new String(archivo.getBytes(), StandardCharsets.UTF_8);
        return bloqueoService.cargar(archivo.getOriginalFilename(), contenido, periodo);
    }

    @PostMapping(path = "/carga-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CargaBloqueosMultipleResponse cargarMultiples(@RequestParam("archivos") List<MultipartFile> archivos,
                                                         @RequestParam(required = false) Long ejecucionId)
            throws IOException {
        if (archivos.isEmpty() || archivos.size() > 2) {
            throw new IllegalArgumentException("Seleccione uno o dos archivos mensuales de bloqueos");
        }
        java.util.ArrayList<BloqueoService.ArchivoMensual> entradas = new java.util.ArrayList<>();
        for (MultipartFile archivo : archivos) {
            if (archivo.isEmpty()) throw new IllegalArgumentException("El archivo está vacío: " + archivo.getOriginalFilename());
            entradas.add(new BloqueoService.ArchivoMensual(archivo.getOriginalFilename(),
                    new String(archivo.getBytes(), StandardCharsets.UTF_8)));
        }
        // Con ejecucionId (p. ej. Día a Día en marcha) los meses cargados se habilitan para esa ejecución.
        return ejecucionId == null ? bloqueoService.cargarMultiples(entradas)
                : bloqueoService.cargarParaEjecucion(ejecucionId, entradas);
    }

    @GetMapping
    public List<BloqueoResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant vigenteEn) {
        return bloqueoService.listar(desde, hasta, vigenteEn);
    }

    @GetMapping("/{id}")
    public BloqueoResponse obtener(@PathVariable Long id) {
        return bloqueoService.obtener(id);
    }
}
