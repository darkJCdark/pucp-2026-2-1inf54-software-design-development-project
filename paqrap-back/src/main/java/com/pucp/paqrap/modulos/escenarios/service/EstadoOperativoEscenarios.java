package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.escenarios.entity.ScenarioType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.pedidos.entity.OrderStatus;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderStatusHistoryEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.pedidos.repository.OrderStatusHistoryRepository;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aísla la operación virtual de FIVE_DAY/COLLAPSE de la operación real DAY_TO_DAY.
 *
 * <p>El catálogo de pedidos, la definición de la flota y los parámetros se comparten; el estado, la posición,
 * la carga, la entrega y el historial de una simulación NO se escriben sobre las filas de Día a Día.
 * Inventario, averías, planes y resultados ya tienen executionId en sus propios módulos.
 *
 * <p>Las copias virtuales viven tanto como los relojes/planes en memoria del motor. Los resultados consolidados,
 * planes y kardex continúan persistiendo. Tras reiniciar no se reanuda una simulación (política existente).
 */
@Service
public class EstadoOperativoEscenarios {
    private static final Comparator<OrderEntity> POR_REGISTRO = Comparator
            .comparing(OrderEntity::getRegisteredAt).thenComparing(OrderEntity::getOrderId);
    private static final Comparator<OrderEntity> POR_PLAZO = Comparator
            .comparing(OrderEntity::getDeadline).thenComparing(OrderEntity::getOrderId);

    private final OrderRepository pedidos;
    private final VehicleRepository vehiculos;
    private final WarehouseRepository almacenes;
    private final OrderStatusHistoryRepository historiales;
    private final Map<Long, EstadoSimulado> simulaciones = new ConcurrentHashMap<>();

    public EstadoOperativoEscenarios(OrderRepository pedidos, VehicleRepository vehiculos,
                                     WarehouseRepository almacenes, OrderStatusHistoryRepository historiales) {
        this.pedidos = pedidos;
        this.vehiculos = vehiculos;
        this.almacenes = almacenes;
        this.historiales = historiales;
    }

    /** Se llama antes de preparar el ejecutor; nunca modifica las entidades JPA del catálogo. */
    public void iniciar(long id, ScenarioType tipo, Instant inicio) {
        simulaciones.remove(id);
        if (tipo == ScenarioType.DAY_TO_DAY) {
            return;
        }
        Instant fin = tipo.duracionSimulada().map(inicio::plus).orElse(null);
        EstadoSimulado estado = new EstadoSimulado(inicio, fin);
        Location central = almacenes.findAll().stream()
                .filter(a -> a.getWarehouseKind() == WarehouseKind.CENTRAL)
                .map(a -> new Location(a.getX(), a.getY())).findFirst().orElse(new Location(27, 14));
        for (VehicleEntity original : vehiculos.findAll()) {
            VehicleEntity copia = new VehicleEntity(original.getVehicleId(), original.getVehicleType(),
                    original.getOperationalStatus() == VehicleOperationalStatus.UNAVAILABLE
                            ? VehicleOperationalStatus.UNAVAILABLE : VehicleOperationalStatus.AVAILABLE);
            copia.moverA(central.x(), central.y(), 0);
            estado.vehiculos.put(copia.getVehicleId(), copia);
        }
        estado.agregar(id, pedidos.catalogoDelPeriodo(inicio, fin));
        simulaciones.put(id, estado);
    }

    /** Congela la vista final: una importación posterior no altera una simulación ya cerrada. */
    public void finalizar(long id) {
        EstadoSimulado estado = simulaciones.get(id);
        if (estado != null) estado.activa = false;
    }

    public boolean aislada(Long id) {
        return id != null && simulaciones.containsKey(id);
    }

    public Optional<OrderEntity> pedido(long id, String pedidoId) {
        EstadoSimulado estado = simulaciones.get(id);
        return estado == null ? pedidos.findById(pedidoId).filter(p -> pertenece(id, p.getOrderId()))
                : Optional.ofNullable(estado.pedidos.get(pedidoId));
    }

    public Optional<VehicleEntity> vehiculo(long id, String vehiculoId) {
        EstadoSimulado estado = simulaciones.get(id);
        return estado == null ? vehiculos.findById(vehiculoId)
                : Optional.ofNullable(estado.vehiculos.get(vehiculoId));
    }

    public List<VehicleEntity> vehiculos(long id) {
        EstadoSimulado estado = simulaciones.get(id);
        return estado == null ? vehiculos.findAll() : estado.vehiculos.values().stream()
                .sorted(Comparator.comparing(VehicleEntity::getVehicleId)).toList();
    }

    /** Catálogo de la copia virtual, con su estado local. Solo se utiliza si aislada(id) es true. */
    public List<OrderEntity> catalogo(long id) {
        EstadoSimulado estado = simulaciones.get(id);
        if (estado == null) {
            throw new IllegalStateException("La ejecución " + id + " no tiene estado virtual");
        }
        return estado.ordenados;
    }

    public List<OrderEntity> registrados(long id, Instant inicio, Instant instante, Pageable pagina) {
        if (!aislada(id)) {
            return pedidos.registradosEnDesde(inicio, instante, pagina);
        }
        return catalogo(id).stream().filter(p -> !p.getRegisteredAt().isBefore(inicio)
                        && !p.getRegisteredAt().isAfter(instante) && p.getDeadline().isAfter(instante)
                        && p.getStatus() == OrderStatus.REGISTERED)
                .skip(pagina.getOffset()).limit(pagina.getPageSize()).toList();
    }

    public List<OrderEntity> incumplidos(long id, Instant inicio, Instant instante, Pageable pagina) {
        if (!aislada(id)) {
            return pedidos.incumplidosDesde(inicio, instante, pagina);
        }
        return catalogo(id).stream().filter(p -> !p.getRegisteredAt().isBefore(inicio)
                        && !p.getDeadline().isAfter(instante)
                        && (p.getStatus() != OrderStatus.DELIVERED || p.getDeliveredAt().isAfter(p.getDeadline())))
                .sorted(POR_PLAZO).skip(pagina.getOffset()).limit(pagina.getPageSize()).toList();
    }

    public record Resumen(long llegados, long pendientes, long vencidosSinEntregar) { }

    public Resumen resumen(long id, Instant inicio, Instant instante) {
        if (!aislada(id)) {
            return new Resumen(pedidos.countByRegisteredAtGreaterThanEqualAndRegisteredAtLessThanEqual(inicio, instante),
                    pedidos.contarPendientesEnDesde(inicio, instante), pedidos.contarVencidosSinEntregar(inicio, instante));
        }
        long llegados = 0, pendientes = 0, vencidos = 0;
        for (OrderEntity pedido : catalogo(id)) {
            if (pedido.getRegisteredAt().isAfter(instante)) break;
            if (pedido.getRegisteredAt().isBefore(inicio)) continue;
            llegados++;
            if (pedido.getStatus() != OrderStatus.DELIVERED) {
                if (pedido.getDeadline().isAfter(instante)) pendientes++;
                else vencidos++;
            }
        }
        return new Resumen(llegados, pendientes, vencidos);
    }

    public List<String> idsEn(long id, Instant instante) {
        if (!aislada(id)) return pedidos.idsRegistradosEn(instante);
        return catalogo(id).stream().filter(p -> p.getRegisteredAt().equals(instante))
                .map(OrderEntity::getOrderId).toList();
    }

    public List<String> idsEntre(long id, Instant desde, Instant hasta) {
        if (!aislada(id)) return pedidos.idsRegistradosEntre(desde, hasta);
        EstadoSimulado estado = simulaciones.get(id);
        // Recoge también nuevas entradas escritas directamente al catálogo durante la ejecución.
        if (estado.activa) estado.agregar(id, pedidos.catalogoRegistradoEntre(desde, hasta));
        return catalogo(id).stream().filter(p -> p.getRegisteredAt().isAfter(desde)
                        && !p.getRegisteredAt().isAfter(hasta)).map(OrderEntity::getOrderId).toList();
    }

    public void guardarHistorial(long id, OrderEntity pedido, OrderStatus estado, Instant instante, String vehiculoId) {
        OrderStatusHistoryEntity cambio = OrderStatusHistoryEntity.cambio(pedido, estado, instante, vehiculoId);
        EstadoSimulado simulado = simulaciones.get(id);
        if (simulado == null) historiales.save(cambio);
        else simulado.historiales.computeIfAbsent(pedido.getOrderId(), clave -> new CopyOnWriteArrayList<>()).add(cambio);
    }

    public List<OrderStatusHistoryEntity> historial(long id, String pedidoId) {
        EstadoSimulado estado = simulaciones.get(id);
        if (estado == null) return historiales.findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc(pedidoId);
        return List.copyOf(estado.historiales.getOrDefault(pedidoId, List.of()));
    }

    /** El lote solo entra en las copias virtuales si la transacción de importación/registro se confirma. */
    public void incorporarAlConfirmar(Long destino, List<OrderEntity> nuevos) {
        List<OrderEntity> copias = nuevos.stream().map(EstadoOperativoEscenarios::copiaRegistrada).toList();
        Runnable incorporar = () -> simulaciones.forEach((id, estado) -> {
            if (estado.activa && (destino == null || destino.equals(id))) estado.agregar(id, copias);
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { incorporar.run(); }
            });
        } else {
            incorporar.run();
        }
    }

    /** Un pedido manual de simulación no entra a otra ejecución ni a Día a Día. */
    public static boolean pertenece(long id, String pedidoId) {
        return !pedidoId.startsWith("SIM-") || pedidoId.startsWith("SIM-" + id + "-");
    }

    private static OrderEntity copiaRegistrada(OrderEntity original) {
        return new OrderEntity(original.getOrderId(), original.getClientId(),
                new Location(original.getDestinationX(), original.getDestinationY()), original.getPackages(),
                original.getRegisteredAt(), original.getDeliveryType(), original.getPromisedHours());
    }

    private static final class EstadoSimulado {
        final Instant inicio;
        final Instant fin;
        volatile boolean activa = true;
        final Map<String, OrderEntity> pedidos = new ConcurrentHashMap<>();
        final Map<String, VehicleEntity> vehiculos = new ConcurrentHashMap<>();
        final Map<String, List<OrderStatusHistoryEntity>> historiales = new ConcurrentHashMap<>();
        volatile List<OrderEntity> ordenados = List.of();

        EstadoSimulado(Instant inicio, Instant fin) { this.inicio = inicio; this.fin = fin; }

        synchronized void agregar(long id, List<OrderEntity> nuevos) {
            boolean cambio = false;
            for (OrderEntity original : nuevos) {
                if (original.getRegisteredAt().isBefore(inicio)
                        || (fin != null && !original.getRegisteredAt().isBefore(fin))
                        // M- corresponde al registro manual real; SIM-<id>- al manual virtual.
                        || original.getOrderId().startsWith("M-")
                        || !pertenece(id, original.getOrderId())) continue;
                if (pedidos.containsKey(original.getOrderId())) continue;
                OrderEntity copia = copiaRegistrada(original);
                if (pedidos.putIfAbsent(copia.getOrderId(), copia) == null) {
                    historiales.put(copia.getOrderId(), new CopyOnWriteArrayList<>(
                            List.of(OrderStatusHistoryEntity.registro(copia))));
                    cambio = true;
                }
            }
            if (cambio) ordenados = pedidos.values().stream().sorted(POR_REGISTRO).toList();
        }
    }
}
