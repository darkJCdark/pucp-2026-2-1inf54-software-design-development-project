package com.pucp.paqrap.modulos.almacenes.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.almacenes.persistence.InventoryMovementEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Inventario de los almacenes dentro de una ejecución de escenario, calculado desde el kardex
 * ({@code inventory_movements}). Un almacén intermedio empieza con su stock inicial, baja con cada despacho y vuelve
 * a su capacidad con la recarga diaria. El central tiene stock ilimitado.
 */
@Service
@Transactional(readOnly = true)
public class InventarioService {

    private final WarehouseRepository almacenRepository;
    private final InventoryMovementRepository movimientoRepository;

    public InventarioService(WarehouseRepository almacenRepository, InventoryMovementRepository movimientoRepository) {
        this.almacenRepository = almacenRepository;
        this.movimientoRepository = movimientoRepository;
    }

    /** Stock de cada almacén en {@code instante}; vacío para el central (ilimitado). Ordenados por id. */
    public Map<String, Optional<Integer>> stocks(long ejecucionId, Instant instante) {
        Map<String, Optional<Integer>> stocks = new LinkedHashMap<>();
        almacenRepository.findAll().stream()
                .sorted((a, b) -> a.getWarehouseId().compareTo(b.getWarehouseId()))
                .forEach(almacen -> stocks.put(almacen.getWarehouseId(), stock(ejecucionId, almacen, instante)));
        return stocks;
    }

    /**
     * Registra la salida de paquetes de un almacén hacia un pedido. Pensado para la integración del planificador
     * cuando un vehículo carga en un almacén. Falla si un intermedio no tiene stock suficiente.
     */
    @Transactional
    public void registrarDespacho(long ejecucionId, String almacenId, String pedidoId, int cantidad, Instant instante) {
        WarehouseEntity almacen = almacenRepository.findById(almacenId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Almacén", almacenId));
        Optional<Integer> disponible = stock(ejecucionId, almacen, instante);
        if (disponible.isPresent() && disponible.get() < cantidad) {
            throw new ReglaNegocioException("El almacén " + almacenId + " solo tiene " + disponible.get()
                    + " paquetes; no puede despachar " + cantidad);
        }
        movimientoRepository.save(InventoryMovementEntity.despacho(almacenId, ejecucionId, pedidoId, cantidad, instante));
    }

    /** Recarga diaria: devuelve cada almacén intermedio a su capacidad. Devuelve cuántos almacenes se recargaron. */
    @Transactional
    public int recargarIntermedios(long ejecucionId, Instant instante) {
        int recargados = 0;
        for (WarehouseEntity almacen : almacenRepository.findAll()) {
            if (almacen.getWarehouseKind() == WarehouseKind.CENTRAL) {
                continue;
            }
            int faltante = almacen.toDomain().capacity() - stock(ejecucionId, almacen, instante).orElseThrow();
            if (faltante > 0) {
                movimientoRepository.save(InventoryMovementEntity.recarga(almacen.getWarehouseId(), ejecucionId,
                        faltante, instante));
                recargados++;
            }
        }
        return recargados;
    }

    private Optional<Integer> stock(long ejecucionId, WarehouseEntity almacen, Instant instante) {
        if (almacen.getWarehouseKind() == WarehouseKind.CENTRAL) {
            return Optional.empty();
        }
        String id = almacen.getWarehouseId();
        Optional<Instant> ultimaRecarga = movimientoRepository.ultimaRecarga(id, ejecucionId, instante);
        int base = ultimaRecarga.isPresent() ? almacen.toDomain().capacity() : almacen.getInitialStock();
        long despachado = movimientoRepository.despachadoEntre(id, ejecucionId, ultimaRecarga.orElse(null), instante);
        return Optional.of((int) (base - despachado));
    }
}
