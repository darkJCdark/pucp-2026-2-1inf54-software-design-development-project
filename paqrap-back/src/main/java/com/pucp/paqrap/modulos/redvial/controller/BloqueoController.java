package com.pucp.paqrap.modulos.redvial.controller;

import com.pucp.paqrap.modulos.redvial.dto.BloqueoResponse;
import com.pucp.paqrap.modulos.redvial.dto.CargaBloqueosResponse;
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
