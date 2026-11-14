package com.pucp.paqrap.modulos.redvial.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.dto.BloqueoResponse;
import com.pucp.paqrap.modulos.redvial.dto.CargaBloqueosResponse;
import com.pucp.paqrap.modulos.redvial.dto.CargaBloqueosMultipleResponse;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Carga de bloqueos planificados (CU-05) y consulta de bloqueos por periodo. */
@Service
@Transactional(readOnly = true)
public class BloqueoService {

    /** Nombre oficial de los archivos mensuales: bloqueo.AAMM.txt (p. ej. bloqueo.2601.txt = enero 2026). */
    private static final Pattern NOMBRE_ARCHIVO = Pattern.compile("^bloqueo\\.(\\d{2})(\\d{2})\\.txt$",
            Pattern.CASE_INSENSITIVE);
    private static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;

    private final RoadBlockRepository bloqueoRepository;
    private final CargadorBloqueos cargador = new CargadorBloqueos();

    public BloqueoService(RoadBlockRepository bloqueoRepository) {
        this.bloqueoRepository = bloqueoRepository;
    }

    /**
     * Registra los bloqueos de un archivo mensual. Reemplaza los bloqueos que ya empezaban en ese mes, de modo que
     * cargar dos veces el mismo archivo no los duplica. Si una línea es inválida no se guarda nada.
     */
    @Transactional
    public CargaBloqueosResponse cargar(String nombreArchivo, String contenido, YearMonth periodo) {
        YearMonth periodoEfectivo = periodo != null ? periodo : periodoDesdeNombre(nombreArchivo);
        List<RoadBlock> bloqueos = cargador.cargar(contenido.replace("﻿", "").lines().toList(),
                periodoEfectivo, ZONA);

        Instant inicioMes = periodoEfectivo.atDay(1).atStartOfDay(ZONA).toInstant();
        Instant inicioMesSiguiente = periodoEfectivo.plusMonths(1).atDay(1).atStartOfDay(ZONA).toInstant();
        int reemplazados = bloqueoRepository.borrarQueEmpiezanEntre(inicioMes, inicioMesSiguiente);

        bloqueoRepository.saveAll(bloqueos.stream().map(RoadBlockEntity::desde).toList());
        return new CargaBloqueosResponse(nombreArchivo, periodoEfectivo, reemplazados, bloqueos.size());
    }

    public record ArchivoMensual(String nombre, String contenido) { }

    /** Carga atómica de hasta dos meses; el reemplazo sigue limitado al mes de cada archivo. */
    @Transactional
    public CargaBloqueosMultipleResponse cargarMultiples(List<ArchivoMensual> archivos) {
        if (archivos == null || archivos.isEmpty() || archivos.size() > 2) {
            throw new IllegalArgumentException("Seleccione uno o dos archivos mensuales de bloqueos");
        }
        java.util.Set<YearMonth> periodos = new java.util.HashSet<>();
        for (ArchivoMensual archivo : archivos) {
            if (archivo == null || archivo.contenido() == null || archivo.contenido().isBlank()) {
                throw new IllegalArgumentException("Ningún archivo puede estar vacío");
            }
            YearMonth periodo = periodoDesdeNombre(archivo.nombre());
            if (!periodos.add(periodo)) throw new IllegalArgumentException("Mes de bloqueos repetido: " + periodo);
            cargador.cargar(archivo.contenido().replace("\uFEFF", "").lines().toList(), periodo, ZONA);
        }
        java.util.ArrayList<CargaBloqueosResponse> resultados = new java.util.ArrayList<>();
        for (ArchivoMensual archivo : archivos) resultados.add(cargar(archivo.nombre(), archivo.contenido(), null));
        return CargaBloqueosMultipleResponse.de(resultados);
    }

    /** Si se indica {@code vigenteEn}, devuelve los bloqueos activos en ese instante; si no, los que se cruzan con [desde, hasta). */
    public List<BloqueoResponse> listar(Instant desde, Instant hasta, Instant vigenteEn) {
        if (vigenteEn != null) {
            desde = vigenteEn;
            hasta = vigenteEn.plusNanos(1);
        } else if (desde != null && hasta != null && !hasta.isAfter(desde)) {
            throw new IllegalArgumentException("'hasta' debe ser posterior a 'desde'");
        }
        return bloqueoRepository.buscarQueSeCruzan(desde, hasta).stream()
                .map(BloqueoResponse::de)
                .toList();
    }

    public BloqueoResponse obtener(Long id) {
        return bloqueoRepository.findById(id)
                .map(BloqueoResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Bloqueo", id));
    }

    private YearMonth periodoDesdeNombre(String nombreArchivo) {
        Matcher coincidencia = NOMBRE_ARCHIVO.matcher(nombreArchivo == null ? "" : nombreArchivo);
        if (!coincidencia.matches()) {
            throw new IllegalArgumentException("No se pudo deducir el periodo del archivo '" + nombreArchivo
                    + "': use el nombre bloqueo.AAMM.txt o indique el parámetro periodo (AAAA-MM)");
        }
        try {
            return YearMonth.of(2000 + Integer.parseInt(coincidencia.group(1)), Integer.parseInt(coincidencia.group(2)));
        } catch (DateTimeException excepcion) {
            throw new IllegalArgumentException("Mes inválido en el nombre del archivo '" + nombreArchivo + "'");
        }
    }
}
