package com.pucp.paqrap.modulos.pedidos.service;

import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.pedidos.dto.PeriodoCargadoResponse;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Qué meses de pedidos están cargados y si cubren el periodo de un escenario. Un mes cuenta como cargado si hay
 * pedidos leídos de su archivo {@code ventas.AAAAMM.txt}, cuyo id es {@code AAAAMM-NNNNN} (ver {@link CargadorPedidos});
 * los pedidos registrados a mano ({@code M-...}) no cuentan.
 */
@Service
public class CoberturaPedidosService {

    private static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;
    private static final Pattern MES_DE_ARCHIVO = Pattern.compile("\\d{6}");

    private final OrderRepository pedidoRepository;

    public CoberturaPedidosService(OrderRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    /** Meses cargados en orden cronológico, con cuántos pedidos tiene cada uno. */
    @Transactional(readOnly = true)
    public List<PeriodoCargadoResponse> periodosCargados() {
        return pedidoRepository.contarCargadosPorMes().stream()
                .filter(fila -> fila[0] != null && MES_DE_ARCHIVO.matcher(fila[0].toString()).matches())
                .map(fila -> new PeriodoCargadoResponse(formatear(fila[0].toString()), ((Number) fila[1]).longValue()))
                .sorted(Comparator.comparing(PeriodoCargadoResponse::periodo))
                .toList();
    }

    /**
     * Exige pedidos cargados en cada mes (hora de Lima) entre {@code desde} y {@code hasta}, ambos incluidos.
     *
     * @throws ReglaNegocioException (409) con los meses que faltan y los archivos a cargar
     */
    @Transactional(readOnly = true)
    public void validarCobertura(Instant desde, Instant hasta) {
        Objects.requireNonNull(desde, "desde es obligatorio");
        Instant fin = hasta == null || hasta.isBefore(desde) ? desde : hasta;
        YearMonth primero = YearMonth.from(desde.atZone(ZONA));
        YearMonth ultimo = YearMonth.from(fin.atZone(ZONA));

        List<YearMonth> faltantes = new ArrayList<>();
        for (YearMonth mes = primero; !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            if (!pedidoRepository.existsByOrderIdStartingWith(prefijo(mes))) {
                faltantes.add(mes);
            }
        }
        if (!faltantes.isEmpty()) {
            throw new ReglaNegocioException(mensaje(faltantes));
        }
    }

    private static String prefijo(YearMonth mes) {
        return String.format("%04d%02d-", mes.getYear(), mes.getMonthValue());
    }

    private static String formatear(String aaaamm) {
        return aaaamm.substring(0, 4) + "-" + aaaamm.substring(4, 6);
    }

    private static String mensaje(List<YearMonth> faltantes) {
        String meses = faltantes.stream().map(YearMonth::toString).collect(Collectors.joining(", "));
        String archivos = faltantes.stream()
                .map(mes -> String.format("ventas.%04d%02d.txt", mes.getYear(), mes.getMonthValue()))
                .collect(Collectors.joining(", "));
        return "No hay pedidos cargados para " + meses + ". Cargue " + archivos
                + " antes de iniciar el escenario.";
    }
}
