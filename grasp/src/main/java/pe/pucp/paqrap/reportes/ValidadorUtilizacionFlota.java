package pe.pucp.paqrap.reportes;

import pe.edu.pucp.paqrap.planner.domain.VehicleType;
import pe.edu.pucp.paqrap.planner.route.DeliveryRoute;
import pe.edu.pucp.paqrap.planner.route.DeliveryStop;
import pe.edu.pucp.paqrap.planner.route.OperationalPlan;
import pe.edu.pucp.paqrap.planner.route.RouteStop;
import pe.edu.pucp.paqrap.planner.route.WarehouseVisit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Compara un OperationalPlan ya construido (de GRASP o de SA -- funciona
 * igual para ambos, ya que ambos producen el mismo tipo de plan sobre el
 * dominio compartido) contra las metas de utilizacion de la hoja "Flota"
 * del Excel de preguntas y respuestas: NroViajes minimo y Carga Total
 * Aprox. Minima, por tipo de vehiculo.
 *
 * DECISION DE DISEÑO A CONFIRMAR: se implementa como validacion POSTERIOR
 * a la corrida (reporta si no se alcanzo la meta), no como restriccion
 * dura dentro de la construccion del plan. Razon: forzar un numero minimo
 * de viajes independientemente de la demanda real podria obligar al
 * algoritmo a generar viajes vacios o redundantes solo para "cumplir
 * cuota", lo cual no tiene sentido de negocio y ademas puede chocar con el
 * objetivo de minimizar costo. Si la intencion es que sea una restriccion
 * dura real, es un cambio distinto y mas invasivo -- avisar antes.
 *
 * "Numero de viajes" en el modelo unificado: una DeliveryRoute puede
 * representar VARIOS viajes de un mismo vehiculo (cada WarehouseVisit,
 * incluido el regreso final, cierra un viaje) -- a diferencia del Ruta
 * original de GRASP, donde 1 Ruta = 1 viaje siempre. Por eso se cuenta el
 * numero de WarehouseVisit por ruta, no el numero de rutas.
 */
public class ValidadorUtilizacionFlota {

    public record ObjetivoUtilizacion(double nroViajesMinimo, int cargaTotalAproxMinima) {}

    // Valores de la hoja "Flota" (10 autos, 15 motos, 12 bicicletas).
    public static final Map<VehicleType, ObjetivoUtilizacion> OBJETIVOS_HOJA_FLOTA = Map.of(
        VehicleType.CAR,        new ObjetivoUtilizacion(3.0, 720),
        VehicleType.MOTORCYCLE, new ObjetivoUtilizacion(6.0, 720),
        VehicleType.BICYCLE,    new ObjetivoUtilizacion(2.1, 96)
    );

    private final Map<VehicleType, ObjetivoUtilizacion> objetivos;

    public ValidadorUtilizacionFlota(Map<VehicleType, ObjetivoUtilizacion> objetivos) {
        this.objetivos = objetivos;
    }

    public List<ReporteUtilizacion> validar(OperationalPlan plan, Map<VehicleType, Integer> cantidadFlotaPorTipo) {
        Map<VehicleType, List<DeliveryRoute>> rutasPorTipo = plan.routes().stream()
                .collect(Collectors.groupingBy(r -> r.vehicle().type()));

        List<ReporteUtilizacion> reportes = new ArrayList<>();
        for (Map.Entry<VehicleType, ObjetivoUtilizacion> e : objetivos.entrySet()) {
            VehicleType tipo = e.getKey();
            ObjetivoUtilizacion objetivo = e.getValue();
            int cantidadFlota = cantidadFlotaPorTipo.getOrDefault(tipo, 0);

            List<DeliveryRoute> rutasDeEsteTipo = rutasPorTipo.getOrDefault(tipo, List.of());
            int totalViajes = rutasDeEsteTipo.stream().mapToInt(this::contarViajes).sum();
            int cargaTotal = rutasDeEsteTipo.stream().mapToInt(this::cargaEntregada).sum();
            double viajesPromedioPorVehiculo = cantidadFlota == 0 ? 0 : (double) totalViajes / cantidadFlota;

            reportes.add(new ReporteUtilizacion(tipo, viajesPromedioPorVehiculo, objetivo.nroViajesMinimo(),
                    cargaTotal, objetivo.cargaTotalAproxMinima()));
        }
        return reportes;
    }

    private int contarViajes(DeliveryRoute ruta) {
        return (int) ruta.stops().stream().filter(s -> s instanceof WarehouseVisit).count();
    }

    private int cargaEntregada(DeliveryRoute ruta) {
        return ruta.stops().stream()
                .filter(s -> s instanceof DeliveryStop)
                .mapToInt(s -> ((DeliveryStop) s).deliveredPackages())
                .sum();
    }

    public record ReporteUtilizacion(VehicleType tipo, double viajesPromedioPorVehiculo, double viajesMinimoEsperado,
                                      int cargaTotalLograda, int cargaTotalMinimaEsperada) {
        public boolean cumpleViajes() { return viajesPromedioPorVehiculo >= viajesMinimoEsperado; }
        public boolean cumpleCarga() { return cargaTotalLograda >= cargaTotalMinimaEsperada; }

        @Override
        public String toString() {
            return String.format("%-12s viajes/vehiculo=%.2f (meta>=%.1f, %s)   carga total=%d (meta>=%d, %s)",
                    tipo, viajesPromedioPorVehiculo, viajesMinimoEsperado, cumpleViajes() ? "OK" : "POR DEBAJO",
                    cargaTotalLograda, cargaTotalMinimaEsperada, cumpleCarga() ? "OK" : "POR DEBAJO");
        }
    }
}
