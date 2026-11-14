package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.redvial.entity.Location;

/**
 * Traduce una ruta vigente a un estado operacional coherente con el
 * instante de replanificación.
 *
 * No usa la coordenada persistida, que solo representa el último evento
 * discreto ejecutado. La ubicación se deriva de los RoadLeg programados:
 *
 * - si está detenido, usa el nodo actual;
 * - si está en mitad de una calle, usa el siguiente nodo seguro;
 * - la carga se deriva de la programación de paradas;
 * - availableAt conserva el fin de la ruta activa, para impedir que el
 *   planificador general abra simultáneamente una segunda ruta al mismo
 *   vehículo.
 */
final class EstadoVehiculoRutaService {

    private final LocalizadorRutaService localizador =
            new LocalizadorRutaService();

    VehicleOperationalState estadoEn(
            RutaVigente vigente,
            Instant ahora
    ) {

        Objects.requireNonNull(
                vigente,
                "vigente es requerida"
        );

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        ScheduledDeliveryRoute programada =
                Objects.requireNonNull(
                        vigente.programada(),
                        "La ruta vigente requiere una programación"
                );

        PosicionRutaEnInstante posicion =
                localizador.localizar(
                        programada,
                        ahora
                );

        Location nodoOperativo =
                posicion.estaEnMovimiento()

                        ? posicion.proximoNodoSeguro()

                        : posicion.ultimoNodoSeguro();

        return new VehicleOperationalState(
                vigente
                        .ruta()
                        .vehicle(),

                VehicleStatus.IN_ROUTE,

                nodoOperativo,

                cargaEn(
                        programada,
                        ahora
                ),

                programada.completedAt()
        );
    }

    int cargaEn(
            ScheduledDeliveryRoute programada,
            Instant ahora
    ) {

        Objects.requireNonNull(
                programada,
                "programada es requerida"
        );

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        DeliveryRoute ruta =
                programada.route();

        if (
                ahora.isBefore(
                        ruta.departureAt()
                )
        ) {

            return ruta.initialLoad();
        }

        List<ScheduledRouteStop> paradas =
                programada.scheduledStops();

        int carga =
                ruta.initialLoad();

        for (
                ScheduledRouteStop parada :
                paradas
        ) {

            if (
                    ahora.isBefore(
                            parada.arrivedAt()
                    )
            ) {

                return parada.loadBefore();
            }

            /*
             * Una transferencia no modifica la carga al llegar al nodo.
             * El movimiento físico ocurre al comenzar el encuentro
             * sincronizado. Mientras el vehículo espera a la contraparte,
             * el snapshot debe conservar loadBefore; de lo contrario una
             * replanificación podría utilizar paquetes todavía no recibidos
             * o descontar paquetes que el donante aún conserva.
             */
            if (
                    parada.stop() instanceof TransferStop
                    && ahora.isBefore(parada.serviceStartedAt())
            ) {
                return parada.loadBefore();
            }

            carga = parada.loadAfter();

            if (ahora.isBefore(parada.completedAt())) {
                return carga;
            }
        }

        return carga;
    }
}
