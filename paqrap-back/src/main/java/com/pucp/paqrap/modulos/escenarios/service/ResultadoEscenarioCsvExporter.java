package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.escenarios.dto.ResultadoEscenarioResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/** Serializa la representación CSV determinista del resultado final de una ejecución. */
@Component
public class ResultadoEscenarioCsvExporter {

    public static final String ENCABEZADO = "executionId;tipo;estado;inicioSimulado;finSimulado;inicioReal;finReal;"
            + "colapsoEn;totalOrders;deliveredOrders;onTimeOrders;undeliveredOrders;totalDistanceKm;totalCost;"
            + "computationTimeMs;breakdownCount;roadBlockCount;replanningCount";

    public String exportar(ResultadoEscenarioResponse resultado) {
        return ENCABEZADO + "\r\n" + String.join(";",
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
                Integer.toString(resultado.replanningCount())) + "\r\n";
    }

    private static String fecha(Instant instante) {
        return instante == null ? "" : instante.toString();
    }

    private static String decimal(BigDecimal valor) {
        return valor.setScale(2).toPlainString();
    }
}
