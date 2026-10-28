package com.pucp.paqrap.modulos.almacenes.service;

import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventarioServiceTest {

    private static final long EJECUCION = 1L;
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private InventarioService inventario;
    @Autowired
    private WarehouseRepository almacenRepository;
    @Autowired
    private InventoryMovementRepository movimientoRepository;

    @BeforeEach
    void cargarAlmacenes() {
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
        almacenRepository.save(new WarehouseEntity("EAST", WarehouseKind.INTERMEDIATE, 57, 27, 1000));
    }

    @Test
    void elStockBajaConLosDespachosYVuelveALaCapacidadConLaRecarga() {
        assertEquals(Optional.of(1000), inventario.stocks(EJECUCION, T0).get("EAST"));

        inventario.registrarDespacho(EJECUCION, "EAST", "P1", 300, T0.plusSeconds(60));
        var despacho = movimientoRepository.findAll().getFirst();
        assertEquals(1000, despacho.getStockBefore());
        assertEquals(700, despacho.getStockAfter());
        assertEquals(Optional.of(1000), inventario.stocks(EJECUCION, T0).get("EAST"), "aún no ocurre el despacho");
        assertEquals(Optional.of(700), inventario.stocks(EJECUCION, T0.plusSeconds(3600)).get("EAST"));

        assertEquals(1, inventario.recargarIntermedios(EJECUCION, T0.plusSeconds(7200)));
        var recarga = movimientoRepository.findAll().stream()
                .filter(movimiento -> movimiento.getMovementType().name().equals("RECHARGE"))
                .findFirst().orElseThrow();
        assertEquals(700, recarga.getStockBefore());
        assertEquals(1000, recarga.getStockAfter());
        assertEquals(Optional.of(1000), inventario.stocks(EJECUCION, T0.plusSeconds(7200)).get("EAST"));

        inventario.registrarDespacho(EJECUCION, "EAST", "P2", 50, T0.plusSeconds(9000));
        assertEquals(Optional.of(950), inventario.stocks(EJECUCION, T0.plusSeconds(9000)).get("EAST"));
    }

    @Test
    void sinConsumoNoRegistraRecarga() {
        assertEquals(0, inventario.recargarIntermedios(EJECUCION, T0));
    }

    @Test
    void noDespachaMasDelStockDeUnIntermedio() {
        inventario.registrarDespacho(EJECUCION, "EAST", "P1", 900, T0);

        assertThrows(ReglaNegocioException.class,
                () -> inventario.registrarDespacho(EJECUCION, "EAST", "P2", 200, T0.plusSeconds(60)));
    }

    @Test
    void elCentralEsIlimitadoYCadaEjecucionLlevaSuPropioInventario() {
        inventario.registrarDespacho(EJECUCION, "CENTRAL", "P1", 5000, T0);
        inventario.registrarDespacho(EJECUCION, "EAST", "P2", 400, T0);

        assertEquals(Optional.empty(), inventario.stocks(EJECUCION, T0).get("CENTRAL"));
        assertEquals(Optional.of(1000), inventario.stocks(2L, T0).get("EAST"));
    }
    @Test
    void elDespachoConLaMismaClaveEsIdempotente() {
        assertEquals(true, inventario.registrarDespacho(EJECUCION, "EAST", null, 300, T0, "RUTA-1-PARADA-2"));
        assertEquals(false, inventario.registrarDespacho(EJECUCION, "EAST", null, 300, T0, "RUTA-1-PARADA-2"));

        assertEquals(1, movimientoRepository.count());
        assertEquals(Optional.of(700), inventario.stocks(EJECUCION, T0).get("EAST"));
    }
}
