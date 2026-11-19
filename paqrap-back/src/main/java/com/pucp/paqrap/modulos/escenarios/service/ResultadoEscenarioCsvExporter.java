package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.escenarios.dto.ResultadoEscenarioResponse;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serializa la representación CSV determinista del resultado final de una ejecución. Al final van la flota con la
 * que corrió (unidades operativas por tipo) y las velocidades por tipo al iniciar y al terminar (difieren si se
 * cambiaron en caliente); quedan vacías si esa información ya no está en memoria (backend reiniciado).
 */
@Component
public class ResultadoEscenarioCsvExporter {

    public static final String ENCABEZADO = "executionId;tipo;estado;inicioSimulado;finSimulado;inicioReal;finReal;"
            + "colapsoEn;totalOrders;deliveredOrders;onTimeOrders;undeliveredOrders;totalDistanceKm;totalCost;"
            + "computationTimeMs;breakdownCount;roadBlockCount;replanningCount;"
            + "autos;motos;bicicletas;velocidadAutosKmH;velocidadMotosKmH;velocidadBicicletasKmH;"
            + "velocidadFinalAutosKmH;velocidadFinalMotosKmH;velocidadFinalBicicletasKmH";

    /** Orden de las columnas de flota: autos, motos, bicicletas. */
    private static final List<VehicleType> TIPOS = List.of(VehicleType.CAR, VehicleType.MOTORCYCLE,
            VehicleType.BICYCLE);

    public String exportar(ResultadoEscenarioResponse resultado) {
        return exportar(resultado, Optional.empty());
    }

    public String exportar(ResultadoEscenarioResponse resultado, Optional<FlotaEjecucion.Resumen> flota) {
        List<String> columnas = new ArrayList<>(List.of(
                Long.toString(resultado.executionId()),
                resultado.tipo().name(),
                resultado.estado().name(),
                fecha(resultado.inicioSimulado()),
                fecha(resultado.finSimulado()),
                fecha(resultado.inicioReal()),
                fecha(resultado.finReal()),
                fecha(resultado.colapsoEn()),
                Integer.toString(resultado.totalOrders()),
                Integer.toString(resultado.deliveredOrders()),
                Integer.toString(resultado.onTimeOrders()),
                Integer.toString(resultado.undeliveredOrders()),
                decimal(resultado.totalDistanceKm()),
                decimal(resultado.totalCost()),
                Long.toString(resultado.computationTimeMs()),
                Integer.toString(resultado.breakdownCount()),
                Integer.toString(resultado.roadBlockCount()),
                Integer.toString(resultado.replanningCount())));
        for (VehicleType tipo : TIPOS) {
            columnas.add(flota.map(f -> f.unidades().get(tipo)).map(String::valueOf).orElse(""));
        }
        agregarVelocidades(columnas, flota.map(FlotaEjecucion.Resumen::velocidadInicial));
        agregarVelocidades(columnas, flota.map(FlotaEjecucion.Resumen::velocidadFinal));
        return ENCABEZADO + "\r\n" + String.join(";", columnas) + "\r\n";
    }

    private static void agregarVelocidades(List<String> columnas, Optional<Map<VehicleType, BigDecimal>> velocidades) {
        for (VehicleType tipo : TIPOS) {
            columnas.add(velocidades.map(v -> v.get(tipo)).map(ResultadoEscenarioCsvExporter::decimal).orElse(""));
        }
    }

    private static String fecha(Instant instante) {
        return instante == null ? "" : instante.toString();
    }

    private static String decimal(BigDecimal valor) {
        return valor.setScale(2).toPlainString();
    }
}
