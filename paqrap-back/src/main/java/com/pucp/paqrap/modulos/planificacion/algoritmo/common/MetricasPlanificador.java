package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import com.pucp.paqrap.modulos.redvial.entity.Location;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Contadores de costo del planificador (Semana 13): llamadas y tiempo de
 * {@link OperationalPlanEvaluator#evaluate} y de {@code RoadNetwork.shortestPath} dentro de
 * {@link RouteScheduler}, más los pares origen-destino distintos consultados.
 *
 * <p>Producción usa {@link #NOOP}: no toma la hora ni guarda nada, por lo que el comportamiento y el
 * resultado del planificador no cambian. El banco de pruebas usa {@link #activas()}.
 *
 * <p>El tiempo de {@code evaluate} incluye el de los caminos mínimos que calcula por dentro.
 */
public final class MetricasPlanificador {

    /** Métricas apagadas; es la instancia que usan los constructores de siempre. */
    public static final MetricasPlanificador NOOP = new MetricasPlanificador(false);

    private final boolean activas;
    private final LongAdder evaluaciones = new LongAdder();
    private final LongAdder nanosEvaluaciones = new LongAdder();
    private final LongAdder caminos = new LongAdder();
    private final LongAdder nanosCaminos = new LongAdder();
    private final Set<ParOrigenDestino> pares = ConcurrentHashMap.newKeySet();
    private final Set<ParConHora> paresConHora = ConcurrentHashMap.newKeySet();

    private MetricasPlanificador(boolean activas) {
        this.activas = activas;
    }

    /** Instancia nueva que sí registra. */
    public static MetricasPlanificador activas() {
        return new MetricasPlanificador(true);
    }

    public boolean estanActivas() {
        return activas;
    }

    /** Marca de inicio para una medición; con métricas apagadas devuelve 0 sin leer el reloj. */
    public long inicio() {
        return activas ? System.nanoTime() : 0L;
    }

    public void registrarEvaluacion(long inicioNanos) {
        if (activas) {
            nanosEvaluaciones.add(System.nanoTime() - inicioNanos);
            evaluaciones.increment();
        }
    }

    public void registrarCamino(long inicioNanos, Location origen, Location destino, Instant salida) {
        if (activas) {
            nanosCaminos.add(System.nanoTime() - inicioNanos);
            caminos.increment();
            pares.add(new ParOrigenDestino(origen, destino));
            paresConHora.add(new ParConHora(origen, destino, salida));
        }
    }

    public Resumen resumen() {
        return new Resumen(evaluaciones.sum(), nanosEvaluaciones.sum() / 1_000_000L, caminos.sum(),
                nanosCaminos.sum() / 1_000_000L, pares.size(), paresConHora.size());
    }

    public void reiniciar() {
        evaluaciones.reset();
        nanosEvaluaciones.reset();
        caminos.reset();
        nanosCaminos.reset();
        pares.clear();
        paresConHora.clear();
    }

    /** Valores acumulados desde la creación o el último {@link #reiniciar()}. */
    public record Resumen(long evaluaciones, long msEvaluaciones, long caminos, long msCaminos,
                          int paresUnicos, int paresConHoraUnicos) {
    }

    private record ParOrigenDestino(Location origen, Location destino) {
    }

    private record ParConHora(Location origen, Location destino, Instant salida) {
    }
}
