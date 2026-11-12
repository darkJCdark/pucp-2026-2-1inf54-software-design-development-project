package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.entity.RoadPath;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Caché LRU de caminos mínimos (Semana 13, paso 6).
 *
 * <p>{@link RoadNetwork#shortestPath} es una función pura: su resultado depende solo del origen, el destino, la hora
 * de salida, el tiempo por calle y los bloqueos. El planificador repite la misma consulta miles de veces por ciclo
 * (98 % de repeticiones medidas en el banco de pruebas), así que guardar el resultado no cambia el plan y evita
 * recalcular el Dijkstra. {@link RoadPath} es inmutable, por lo que el mismo resultado se puede compartir.
 *
 * <p>También se guarda la ausencia de camino ({@code Optional.empty()}). Con capacidad 0 no guarda nada y cada
 * consulta se calcula, igual que antes del caché. Es seguro para hilos: el cálculo se hace fuera del candado, por
 * lo que dos hilos pueden calcular la misma consulta a la vez y ambos obtienen el mismo resultado.
 */
public final class CacheCaminosMinimos {

    /** Capacidad que usan los constructores de siempre de {@link RouteScheduler}. */
    public static final int CAPACIDAD_POR_DEFECTO = 10_000;

    /** Cálculo real de un camino; en producción es {@link RoadNetwork#shortestPath}. */
    @FunctionalInterface
    interface CalculadorCamino {
        Optional<RoadPath> calcular(Location origen, Location destino, Instant salida, Duration porCalle,
                                    List<RoadBlock> bloqueos, Location llegoDesde);
    }

    private final CalculadorCamino calculador;
    private final MetricasPlanificador metricas;
    private final int capacidad;
    private final Map<Clave, Optional<RoadPath>> entradas;
    /** Última lista inmutable de bloqueos vista y su envoltorio, para no recalcular el hash en cada consulta. */
    private List<RoadBlock> ultimaLista;
    private Bloqueos ultimosBloqueos;

    public CacheCaminosMinimos(RoadNetwork red, MetricasPlanificador metricas, int capacidad) {
        this(Objects.requireNonNull(red, "red is required")::shortestPath, metricas, capacidad);
    }

    CacheCaminosMinimos(CalculadorCamino calculador, MetricasPlanificador metricas, int capacidad) {
        this.calculador = Objects.requireNonNull(calculador, "calculador is required");
        this.metricas = Objects.requireNonNull(metricas, "metricas is required");
        if (capacidad < 0) {
            throw new IllegalArgumentException("capacidad must not be negative");
        }
        this.capacidad = capacidad;
        this.entradas = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Clave, Optional<RoadPath>> mayor) {
                return size() > CacheCaminosMinimos.this.capacidad;
            }
        };
    }

    public Optional<RoadPath> obtener(Location origen, Location destino, Instant salida, Duration porCalle,
                                      List<RoadBlock> bloqueos) {
        return obtener(origen, destino, salida, porCalle, bloqueos, null);
    }

    /** @param llegoDesde nodo desde el que se llegó al origen (regla del pedido dentro de un bloqueo); puede ser null */
    public Optional<RoadPath> obtener(Location origen, Location destino, Instant salida, Duration porCalle,
                                      List<RoadBlock> bloqueos, Location llegoDesde) {
        if (capacidad == 0) {
            return calcular(origen, destino, salida, porCalle, bloqueos, llegoDesde);
        }
        Clave clave = new Clave(origen, destino, salida, porCalle, bloqueosDe(bloqueos), llegoDesde);
        Optional<RoadPath> guardado;
        synchronized (entradas) {
            guardado = entradas.get(clave);
        }
        if (guardado != null) {
            metricas.registrarAciertoCache();
            return guardado;
        }
        Optional<RoadPath> calculado = calcular(origen, destino, salida, porCalle, bloqueos, llegoDesde);
        synchronized (entradas) {
            entradas.put(clave, calculado);
        }
        return calculado;
    }

    public int tamanio() {
        synchronized (entradas) {
            return entradas.size();
        }
    }

    public int capacidad() {
        return capacidad;
    }

    /**
     * Reutiliza el envoltorio de la última lista si es la misma lista inmutable (no puede haber cambiado). Una lista
     * mutable se copia en cada consulta, así un cambio posterior del llamador nunca corrompe una clave guardada.
     */
    private Bloqueos bloqueosDe(List<RoadBlock> bloqueos) {
        Objects.requireNonNull(bloqueos, "bloqueos are required");
        synchronized (entradas) {
            if (bloqueos == ultimaLista) {
                return ultimosBloqueos;
            }
        }
        List<RoadBlock> inmutable = List.copyOf(bloqueos);
        Bloqueos envoltorio = new Bloqueos(inmutable);
        if (inmutable == bloqueos) {
            synchronized (entradas) {
                ultimaLista = bloqueos;
                ultimosBloqueos = envoltorio;
            }
        }
        return envoltorio;
    }

    private Optional<RoadPath> calcular(Location origen, Location destino, Instant salida, Duration porCalle,
                                        List<RoadBlock> bloqueos, Location llegoDesde) {
        long inicio = metricas.inicio();
        Optional<RoadPath> camino = Objects.requireNonNull(
                calculador.calcular(origen, destino, salida, porCalle, bloqueos, llegoDesde), "camino calculado nulo");
        metricas.registrarCamino(inicio, origen, destino, salida);
        return camino;
    }

    private record Clave(Location origen, Location destino, Instant salida, Duration porCalle, Bloqueos bloqueos,
                         Location llegoDesde) {
    }

    /**
     * Lista inmutable de bloqueos como parte de la clave, con su hash calculado una sola vez; la igualdad compara
     * primero por referencia, porque el planificador pasa la misma lista en todo un ciclo.
     */
    private static final class Bloqueos {
        private final List<RoadBlock> lista;
        private final int hash;

        private Bloqueos(List<RoadBlock> lista) {
            this.lista = lista;
            this.hash = lista.hashCode();
        }

        @Override
        public boolean equals(Object otro) {
            return this == otro || otro instanceof Bloqueos b && hash == b.hash
                    && (lista == b.lista || lista.equals(b.lista));
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
