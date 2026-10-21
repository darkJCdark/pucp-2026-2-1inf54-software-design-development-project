package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Detecta el colapso logístico (RF_105): el momento en que ya no es posible cumplir el plazo de todos los pedidos.
 * No depende de que SA haya dejado algo sin asignar en un ciclo, porque una metaheurística con presupuesto de tiempo
 * puede no encontrar una solución que sí existe. Se declara con dos señales objetivas:
 * <ul>
 *   <li>{@link Causa#PLAZO_VENCIDO}: un pedido llegó a su plazo sin entregarse, o se entregó después (dato real del
 *       ejecutor del plan). El colapso ocurre en ese plazo.</li>
 *   <li>{@link Causa#PLAZO_INALCANZABLE}: un pedido que aún no salió no llegaría a tiempo ni en el mejor caso: salir
 *       ahora del central, por el camino más corto sin bloqueos (distancia Manhattan), con el tipo de vehículo más
 *       rápido que tenga unidades habilitadas. Como esa cota es optimista, el incumplimiento es seguro.</li>
 * </ul>
 * Se guarda el primer colapso de cada ejecución con las condiciones de ese momento (RF_106).
 */
@Component
public class DetectorColapso {

    private static final Logger log = LoggerFactory.getLogger(DetectorColapso.class);
    private static final int TAMANO_PAGINA = 500;

    public enum Causa { PLAZO_VENCIDO, PLAZO_INALCANZABLE }

    /**
     * @param instante             momento del colapso: el plazo incumplido, o el instante en que se detectó que ya era
     *                             inalcanzable
     * @param pedidoId             pedido que lo causó
     * @param plazo                plazo comprometido de ese pedido
     * @param llegadaMinima        en PLAZO_INALCANZABLE, la llegada del mejor caso; null en PLAZO_VENCIDO
     * @param detectadoEn          instante simulado de la revisión que lo detectó
     * @param pedidosPendientes    pedidos llegados y aún sin entregar en el momento de la detección
     * @param vencidosSinEntregar  pedidos con el plazo ya vencido sin entregar en el momento de la detección
     * @param vehiculosHabilitados vehículos no marcados como no disponibles por el operario
     */
    public record Colapso(Instant instante, Causa causa, String pedidoId, Instant plazo, Instant llegadaMinima,
                          Instant detectadoEn, long pedidosPendientes, long vencidosSinEntregar,
                          long vehiculosHabilitados) {
    }

    private final OrderRepository pedidoRepository;
    private final VehicleRepository vehiculoRepository;
    private final VehicleTypeParametersRepository tipoRepository;
    private final WarehouseRepository almacenRepository;
    private final Map<Long, Colapso> colapsos = new ConcurrentHashMap<>();

    public DetectorColapso(OrderRepository pedidoRepository, VehicleRepository vehiculoRepository,
                           VehicleTypeParametersRepository tipoRepository, WarehouseRepository almacenRepository) {
        this.pedidoRepository = pedidoRepository;
        this.vehiculoRepository = vehiculoRepository;
        this.tipoRepository = tipoRepository;
        this.almacenRepository = almacenRepository;
    }

    /**
     * Revisa la ejecución en {@code instante}; solo se registra el primer colapso. Devuelve el colapso registrado,
     * sea de esta revisión o de una anterior.
     */
    @Transactional(readOnly = true)
    public Optional<Colapso> revisar(long ejecucionId, Instant inicio, Instant instante) {
        Colapso previo = colapsos.get(ejecucionId);
        if (previo != null) {
            return Optional.of(previo);
        }
        List<VehicleEntity> habilitados = vehiculoRepository.findAll().stream()
                .filter(vehiculo -> vehiculo.getOperationalStatus() != VehicleOperationalStatus.UNAVAILABLE)
                .toList();
        Optional<Colapso> colapso = plazoVencido(inicio, instante, habilitados.size())
                .or(() -> plazoInalcanzable(inicio, instante, habilitados));
        colapso.ifPresent(c -> {
            colapsos.put(ejecucionId, c);
            log.warn("Ejecución {}: colapso logístico en {} ({}, pedido {} con plazo {})", ejecucionId, c.instante(),
                    c.causa(), c.pedidoId(), c.plazo());
        });
        return colapso;
    }

    public Optional<Colapso> colapso(long ejecucionId) {
        return Optional.ofNullable(colapsos.get(ejecucionId));
    }

    /** Una ejecución nueva empieza sin colapso. */
    public void reiniciar(long ejecucionId) {
        colapsos.remove(ejecucionId);
    }

    private Optional<Colapso> plazoVencido(Instant inicio, Instant instante, long habilitados) {
        return pedidoRepository.incumplidosDesde(inicio, instante, PageRequest.of(0, 1)).stream().findFirst()
                .map(pedido -> colapso(pedido.getDeadline(), Causa.PLAZO_VENCIDO, pedido, null, inicio, instante,
                        habilitados));
    }

    private Optional<Colapso> plazoInalcanzable(Instant inicio, Instant instante, List<VehicleEntity> habilitados) {
        Optional<WarehouseEntity> central = almacenRepository.findAll().stream()
                .filter(almacen -> almacen.getWarehouseKind() == WarehouseKind.CENTRAL)
                .findFirst();
        Set<VehicleType> tipos = habilitados.stream().map(VehicleEntity::getVehicleType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(VehicleType.class)));
        OptionalDouble velocidad = tipoRepository.findAll().stream()
                .filter(tipo -> tipos.contains(tipo.getVehicleType()))
                .mapToDouble(tipo -> tipo.toDomain().speedKmPerHour())
                .max();
        // Sin central o sin flota no hay cota que calcular; el plazo vencido lo detectará.
        if (central.isEmpty() || velocidad.isEmpty() || velocidad.getAsDouble() <= 0) {
            return Optional.empty();
        }
        for (int pagina = 0; ; pagina++) {
            List<OrderEntity> pendientes = pedidoRepository.registradosEnDesde(inicio, instante,
                    PageRequest.of(pagina, TAMANO_PAGINA));
            for (OrderEntity pedido : pendientes) {
                double km = Math.abs(pedido.getDestinationX() - central.get().getX())
                        + Math.abs(pedido.getDestinationY() - central.get().getY());
                Instant llegadaMinima = instante.plus(
                        Duration.ofMillis(Math.round(km / velocidad.getAsDouble() * 3_600_000)));
                if (llegadaMinima.isAfter(pedido.getDeadline())) {
                    return Optional.of(colapso(instante, Causa.PLAZO_INALCANZABLE, pedido, llegadaMinima, inicio,
                            instante, habilitados.size()));
                }
            }
            if (pendientes.size() < TAMANO_PAGINA) {
                return Optional.empty();
            }
        }
    }

    private Colapso colapso(Instant momento, Causa causa, OrderEntity pedido, Instant llegadaMinima, Instant inicio,
                            Instant instante, long habilitados) {
        return new Colapso(momento, causa, pedido.getOrderId(), pedido.getDeadline(), llegadaMinima, instante,
                pedidoRepository.contarPendientesEnDesde(inicio, instante),
                pedidoRepository.contarVencidosSinEntregar(inicio, instante), habilitados);
    }
}
