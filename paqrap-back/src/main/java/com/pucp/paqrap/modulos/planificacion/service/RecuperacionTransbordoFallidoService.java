package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ContextoProgramacionRuta;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.TransferStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reconstruye las dos rutas después de un encuentro cancelado sin inventar un
 * movimiento de carga. El pedido transferible permanece físicamente en el
 * donante, por lo que la recuperación antepone su entrega a la continuación
 * del donante y elimina la entrega artificial de la ruta receptora.
 *
 * <p>Si no existe todavía un camino ejecutable, el fallo queda pendiente y la
 * cantidad se protege para que el planificador no la asigne a un tercer
 * vehículo mientras sigue a bordo del donante.</p>
 */
final class RecuperacionTransbordoFallidoService {

    private final RouteScheduler scheduler;
    private final CheckpointRutaService checkpoints = new CheckpointRutaService();

    RecuperacionTransbordoFallidoService(RouteScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler es requerido");
    }

    Resultado recuperar(
            Instant ahora,
            Collection<Pendiente> recuperacionesPendientes,
            Collection<Warehouse> almacenes,
            Map<String, VehicleOperationalState> estadosReales,
            OperationalSnapshot snapshot,
            List<RoadBlock> bloqueos
    ) {
        Objects.requireNonNull(ahora, "ahora es requerido");
        Objects.requireNonNull(recuperacionesPendientes, "recuperacionesPendientes son requeridas");
        Objects.requireNonNull(almacenes, "almacenes son requeridos");
        Objects.requireNonNull(estadosReales, "estadosReales son requeridos");
        Objects.requireNonNull(snapshot, "snapshot es requerido");
        Objects.requireNonNull(bloqueos, "bloqueos son requeridos");

        List<RutaVigente> recuperadas = new ArrayList<>();
        Set<String> vehiculosRecuperados = new LinkedHashSet<>();
        Set<String> transferenciasResueltas = new LinkedHashSet<>();
        Map<String, Integer> cantidadesProtegidas = new LinkedHashMap<>();
        List<Pendiente> pendientes = new ArrayList<>();

        for (Pendiente recuperacion : recuperacionesPendientes) {
            FalloTransbordoOperativo fallo = recuperacion.fallo();
            RutaVigente donanteAnterior = recuperacion.rutaDonante();
            RutaVigente receptorAnterior = recuperacion.rutaReceptor();
            VehicleOperationalState estadoDonante = estadosReales.get(fallo.donorVehicleId());
            VehicleOperationalState estadoReceptor = estadosReales.get(fallo.receiverVehicleId());

            TransferStop paradaDonante = buscarTransfer(donanteAnterior, fallo.transferId());
            TransferStop paradaReceptor = buscarTransfer(receptorAnterior, fallo.transferId());
            TransferStop referencia = paradaDonante != null ? paradaDonante : paradaReceptor;

            if (referencia == null || estadoDonante == null
                    || estadoDonante.carriedPackages() < fallo.packages()) {
                pendientes.add(recuperacion);
                protegerCompromisosPendientes(recuperacion, cantidadesProtegidas);
                continue;
            }

            Order pedido = referencia.order();
            try {
                RutaVigente donanteRecuperado = reconstruirDonante(
                        ahora, donanteAnterior, estadoDonante, pedido, fallo,
                        almacenes, snapshot, bloqueos);

                RutaVigente receptorRecuperado = reconstruirReceptor(
                        ahora, receptorAnterior, estadoReceptor, fallo,
                        almacenes, snapshot, bloqueos);

                recuperadas.add(donanteRecuperado);
                vehiculosRecuperados.add(fallo.donorVehicleId());
                if (receptorRecuperado != null) {
                    recuperadas.add(receptorRecuperado);
                    vehiculosRecuperados.add(fallo.receiverVehicleId());
                }
                transferenciasResueltas.add(fallo.transferId());
            } catch (RuntimeException noRecuperableAun) {
                pendientes.add(recuperacion);
                protegerCompromisosPendientes(recuperacion, cantidadesProtegidas);
            }
        }

        return new Resultado(
                List.copyOf(recuperadas),
                Set.copyOf(vehiculosRecuperados),
                Set.copyOf(transferenciasResueltas),
                Map.copyOf(cantidadesProtegidas),
                List.copyOf(pendientes));
    }

    private RutaVigente reconstruirDonante(
            Instant ahora,
            RutaVigente anterior,
            VehicleOperationalState estado,
            Order pedido,
            FalloTransbordoOperativo fallo,
            Collection<Warehouse> almacenes,
            OperationalSnapshot snapshot,
            List<RoadBlock> bloqueos
    ) {
        if (anterior == null) {
            throw new IllegalStateException("No existe la ruta donante interrumpida");
        }
        List<RouteStop> futuro = futuroDespuesDe(anterior, fallo.transferId());
        List<RouteStop> paradas = new ArrayList<>(futuro.size() + 2);
        paradas.add(new DeliveryStop(pedido, fallo.packages()));
        paradas.addAll(futuro);
        asegurarTerminal(paradas, estado.location(), almacenes);
        return programarRecuperacion(
                ahora, anterior, estado, paradas,
                "D", fallo, snapshot, bloqueos);
    }

    private RutaVigente reconstruirReceptor(
            Instant ahora,
            RutaVigente anterior,
            VehicleOperationalState estado,
            FalloTransbordoOperativo fallo,
            Collection<Warehouse> almacenes,
            OperationalSnapshot snapshot,
            List<RoadBlock> bloqueos
    ) {
        if (anterior == null || estado == null) {
            return null;
        }
        List<RouteStop> futuro = new ArrayList<>(futuroDespuesDe(anterior, fallo.transferId()));
        quitarEntregaTransferida(futuro, fallo.orderId(), fallo.packages());

        boolean tieneTrabajo = futuro.stream().anyMatch(stop -> !(stop instanceof WarehouseVisit));
        if (!tieneTrabajo) {
            Warehouse terminal = futuro.stream()
                    .filter(WarehouseVisit.class::isInstance)
                    .map(WarehouseVisit.class::cast)
                    .map(WarehouseVisit::warehouse)
                    .findFirst()
                    .orElse(null);
            if (terminal == null || terminal.location().equals(estado.location())) {
                return null;
            }
        }

        asegurarTerminal(futuro, estado.location(), almacenes);
        return programarRecuperacion(
                ahora, anterior, estado, futuro,
                "R", fallo, snapshot, bloqueos);
    }

    private RutaVigente programarRecuperacion(
            Instant ahora,
            RutaVigente anterior,
            VehicleOperationalState estado,
            List<RouteStop> paradas,
            String rol,
            FalloTransbordoOperativo fallo,
            OperationalSnapshot snapshot,
            List<RoadBlock> bloqueos
    ) {
        String id = idRecuperacion(rol, fallo);
        DeliveryRoute ruta = DeliveryRoute.replanFromCurrentLocation(
                id,
                estado.vehicle(),
                estado.location(),
                estado.carriedPackages(),
                ahora);
        for (RouteStop parada : paradas) {
            ruta = ruta.withAppendedStop(parada);
        }

        Instant turnoConRefrigerio = null;
        try {
            turnoConRefrigerio = checkpoints.crear(anterior, fallo.occurredAt())
                    .turnoConRefrigerioTomado()
                    .orElse(null);
        } catch (RuntimeException ignorado) {
            // La recuperación puede seguir de forma conservadora sin ese dato.
        }

        ScheduledDeliveryRoute programada = scheduler.schedule(
                ruta,
                snapshot,
                bloqueos,
                new ContextoProgramacionRuta(turnoConRefrigerio));

        int capacidad = snapshot.fleetProfile()
                .parametersFor(ruta.vehicle().type())
                .capacity();
        boolean cargaValida = programada.scheduledStops().stream()
                .allMatch(stop -> stop.loadBefore() >= 0 && stop.loadAfter() >= 0
                        && stop.loadBefore() <= capacidad && stop.loadAfter() <= capacidad);
        if (!cargaValida || snapshot.hasVehicleDisruptionDuring(
                ruta.vehicle().id(), ahora, programada.completedAt())) {
            throw new IllegalStateException("La ruta de recuperación no es operacional");
        }

        return new RutaVigente(ruta, snapshot, programada, List.of());
    }


    /**
     * Mientras el encuentro fallido no pueda reconstruirse, ninguna entrega
     * futura de las dos rutas debe reaparecer como carga libre. El modelo
     * histórico conserva solo la carga total del vehículo, no un manifiesto
     * por pedido; por ello esta protección es deliberadamente conservadora.
     *
     * <p>La entrega artificial que el receptor iba a efectuar con la carga
     * recibida se excluye porque esa misma cantidad se protege explícitamente
     * como propiedad del donante.</p>
     */
    private static void protegerCompromisosPendientes(
            Pendiente pendiente,
            Map<String, Integer> cantidadesProtegidas
    ) {
        FalloTransbordoOperativo fallo = pendiente.fallo();
        mergeProtegido(cantidadesProtegidas, fallo.orderId(), fallo.packages(), null);

        protegerEntregasFuturas(
                pendiente.rutaDonante(),
                fallo,
                false,
                cantidadesProtegidas);
        protegerEntregasFuturas(
                pendiente.rutaReceptor(),
                fallo,
                true,
                cantidadesProtegidas);
    }

    private static void protegerEntregasFuturas(
            RutaVigente ruta,
            FalloTransbordoOperativo fallo,
            boolean excluirEntregaTransferida,
            Map<String, Integer> cantidadesProtegidas
    ) {
        if (ruta == null) {
            return;
        }

        List<RouteStop> futuro;
        try {
            futuro = futuroDespuesDe(ruta, fallo.transferId());
        } catch (RuntimeException rutaInconsistente) {
            return;
        }

        boolean transferenciaExcluida = false;
        for (RouteStop stop : futuro) {
            if (!(stop instanceof DeliveryStop entrega)) {
                continue;
            }
            if (excluirEntregaTransferida
                    && !transferenciaExcluida
                    && entrega.order().id().equals(fallo.orderId())
                    && entrega.deliveredPackages() == fallo.packages()) {
                transferenciaExcluida = true;
                continue;
            }
            mergeProtegido(
                    cantidadesProtegidas,
                    entrega.order().id(),
                    entrega.deliveredPackages(),
                    entrega.order().packages());
        }
    }

    private static void mergeProtegido(
            Map<String, Integer> cantidadesProtegidas,
            String pedidoId,
            int cantidad,
            Integer maximoPedido
    ) {
        if (cantidad <= 0) {
            return;
        }
        cantidadesProtegidas.merge(
                pedidoId,
                cantidad,
                (actual, adicional) -> {
                    long total = (long) actual + adicional;
                    if (maximoPedido != null) {
                        total = Math.min(total, maximoPedido);
                    }
                    return (int) Math.min(total, Integer.MAX_VALUE);
                });
    }

    private static List<RouteStop> futuroDespuesDe(RutaVigente ruta, String transferId) {
        List<RouteStop> stops = ruta.ruta().stops();
        int indice = -1;
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i) instanceof TransferStop transfer
                    && transfer.transferId().equals(transferId)) {
                indice = i;
                break;
            }
        }
        if (indice < 0) {
            throw new IllegalStateException("La ruta no contiene el transbordo " + transferId);
        }
        return new ArrayList<>(stops.subList(indice + 1, stops.size()));
    }

    private static TransferStop buscarTransfer(RutaVigente ruta, String transferId) {
        if (ruta == null) {
            return null;
        }
        return ruta.ruta().stops().stream()
                .filter(TransferStop.class::isInstance)
                .map(TransferStop.class::cast)
                .filter(transfer -> transfer.transferId().equals(transferId))
                .findFirst()
                .orElse(null);
    }

    private static void quitarEntregaTransferida(
            List<RouteStop> futuro,
            String orderId,
            int packages
    ) {
        for (int i = 0; i < futuro.size(); i++) {
            RouteStop stop = futuro.get(i);
            if (stop instanceof DeliveryStop entrega
                    && entrega.order().id().equals(orderId)
                    && entrega.deliveredPackages() == packages) {
                futuro.remove(i);
                return;
            }
        }
        throw new IllegalStateException("La ruta receptora no conserva la entrega transferida");
    }

    private static void asegurarTerminal(
            List<RouteStop> paradas,
            Location origen,
            Collection<Warehouse> almacenes
    ) {
        if (!paradas.isEmpty() && paradas.getLast() instanceof WarehouseVisit) {
            return;
        }
        Location referencia = paradas.isEmpty() ? origen : paradas.getLast().location();
        Warehouse terminal = almacenes.stream()
                .min(Comparator
                        .comparingDouble((Warehouse almacen) ->
                                referencia.manhattanDistanceTo(almacen.location()))
                        .thenComparing(almacen -> almacen.isCentral() ? 0 : 1)
                        .thenComparing(Warehouse::id))
                .orElseThrow(() -> new IllegalStateException("No hay almacenes para cerrar la recuperación"));
        paradas.add(new WarehouseVisit(terminal, 0));
    }

    private static String idRecuperacion(String rol, FalloTransbordoOperativo fallo) {
        String base = "TF-REC-" + rol + "-" + Integer.toUnsignedString(fallo.transferId().hashCode(), 36);
        return base.length() <= 64 ? base : base.substring(0, 64);
    }

    record Resultado(
            List<RutaVigente> rutasRecuperadas,
            Set<String> vehiculosRecuperados,
            Set<String> transferenciasResueltas,
            Map<String, Integer> cantidadesProtegidas,
            List<Pendiente> pendientes
    ) {
    }

    record Pendiente(
            FalloTransbordoOperativo fallo,
            RutaVigente rutaDonante,
            RutaVigente rutaReceptor
    ) {
        Pendiente {
            Objects.requireNonNull(fallo, "fallo es requerido");
            Objects.requireNonNull(rutaDonante, "rutaDonante es requerida");
        }
    }
}
