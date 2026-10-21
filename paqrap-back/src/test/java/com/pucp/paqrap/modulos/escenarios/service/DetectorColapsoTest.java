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
import com.pucp.paqrap.modulos.pedidos.entity.DeliveryType;
import com.pucp.paqrap.modulos.pedidos.persistence.OrderEntity;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Central en (27,14); un pedido priorizado de 4 h. Auto a 40 km/h y bicicleta a 12 km/h. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DetectorColapsoTest {

    private static final Instant INICIO = Instant.parse("2026-01-01T05:00:00Z");
    private static final Instant PLAZO = INICIO.plus(Duration.ofHours(4));
    private static final AtomicLong EJECUCIONES = new AtomicLong(5000);

    @Autowired private DetectorColapso detector;
    @Autowired private OrderRepository pedidoRepository;
    @Autowired private VehicleRepository vehiculoRepository;
    @Autowired private VehicleTypeParametersRepository tipoRepository;
    @Autowired private WarehouseRepository almacenRepository;

    private long ejecucion;

    @BeforeEach
    void cargarDatos() {
        ejecucion = EJECUCIONES.incrementAndGet();
        almacenRepository.save(new WarehouseEntity("CENTRAL", WarehouseKind.CENTRAL, 27, 14, null));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.CAR, 24, new BigDecimal("40.00"), new BigDecimal("8.00")));
        tipoRepository.save(new VehicleTypeParametersEntity(VehicleType.BICYCLE, 4, new BigDecimal("12.00"), new BigDecimal("3.00")));
    }

    private OrderEntity pedido(String id, int x, int y) {
        return pedidoRepository.save(new OrderEntity(id, "c-" + id, new Location(x, y), 2, INICIO, DeliveryType.PRIORITY, 4));
    }

    private void vehiculo(String id, VehicleType tipo, VehicleOperationalStatus estado) {
        vehiculoRepository.save(new VehicleEntity(id, tipo, estado));
    }

    @Test
    void sinPedidosIncumplidosNoHayColapso() {
        vehiculo("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE);
        pedido("P1", 30, 14);

        assertTrue(detector.revisar(ejecucion, INICIO, INICIO.plusSeconds(3600)).isEmpty());
    }

    @Test
    void unPedidoEntregadoATiempoNoEsColapso() {
        vehiculo("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE);
        pedido("P1", 30, 14).entregar(PLAZO.minusSeconds(60));

        assertTrue(detector.revisar(ejecucion, INICIO, PLAZO.plusSeconds(3600)).isEmpty());
    }

    @Test
    void unPedidoQueLlegaASuPlazoSinEntregarseEsColapsoEnEsePlazo() {
        vehiculo("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE);
        pedido("P1", 30, 14);
        Instant revision = PLAZO.plusSeconds(600);

        // A 1 h del plazo aún se alcanza (3 km en auto). Más cerca del plazo lo detectaría antes la cota del mejor
        // caso (PLAZO_INALCANZABLE); aquí no hubo revisión entre medio, así que se registra el plazo vencido.
        assertTrue(detector.revisar(ejecucion, INICIO, PLAZO.minusSeconds(3600)).isEmpty(), "aún está en plazo");
        DetectorColapso.Colapso colapso = detector.revisar(ejecucion, INICIO, revision).orElseThrow();

        assertEquals(DetectorColapso.Causa.PLAZO_VENCIDO, colapso.causa());
        assertEquals(PLAZO, colapso.instante());
        assertEquals("P1", colapso.pedidoId());
        assertEquals(revision, colapso.detectadoEn());
        assertNull(colapso.llegadaMinima());
        assertEquals(1, colapso.vencidosSinEntregar());
        assertEquals(1, colapso.vehiculosHabilitados());
    }

    @Test
    void unPedidoEntregadoDespuesDeSuPlazoEsColapso() {
        vehiculo("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE);
        pedido("P1", 30, 14).entregar(PLAZO.plusSeconds(60));

        DetectorColapso.Colapso colapso = detector.revisar(ejecucion, INICIO, PLAZO.plusSeconds(120)).orElseThrow();

        assertEquals(DetectorColapso.Causa.PLAZO_VENCIDO, colapso.causa());
        assertEquals(PLAZO, colapso.instante());
    }

    /** (27,14) -> (70,50) = 79 km: en bicicleta 6,6 h, más que las 4 h del plazo, aun saliendo de inmediato. */
    @Test
    void unPedidoInalcanzableAunEnElMejorCasoEsColapsoAlDetectarlo() {
        vehiculo("TB01", VehicleType.BICYCLE, VehicleOperationalStatus.AVAILABLE);
        pedido("P1", 70, 50);

        DetectorColapso.Colapso colapso = detector.revisar(ejecucion, INICIO, INICIO).orElseThrow();

        assertEquals(DetectorColapso.Causa.PLAZO_INALCANZABLE, colapso.causa());
        assertEquals(INICIO, colapso.instante());
        assertEquals(PLAZO, colapso.plazo());
        assertEquals(INICIO.plusMillis(Math.round(79 / 12.0 * 3_600_000)), colapso.llegadaMinima());
        assertEquals(1, colapso.pedidosPendientes());
    }

    @Test
    void conUnVehiculoMasRapidoElMismoPedidoSiEsAlcanzable() {
        vehiculo("TB01", VehicleType.BICYCLE, VehicleOperationalStatus.AVAILABLE);
        vehiculo("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE);
        pedido("P1", 70, 50);

        assertTrue(detector.revisar(ejecucion, INICIO, INICIO).isEmpty(), "79 km en auto son 2 h");
        // Pasado el último momento de salida (plazo - 1 h 58,5 min), ya no se alcanza ni en auto.
        DetectorColapso.Colapso colapso = detector.revisar(ejecucion, INICIO, PLAZO.minusSeconds(3600)).orElseThrow();
        assertEquals(DetectorColapso.Causa.PLAZO_INALCANZABLE, colapso.causa());
    }

    @Test
    void unVehiculoNoDisponibleNoCuentaParaElMejorCaso() {
        vehiculo("TB01", VehicleType.BICYCLE, VehicleOperationalStatus.AVAILABLE);
        vehiculo("TA01", VehicleType.CAR, VehicleOperationalStatus.UNAVAILABLE);
        pedido("P1", 70, 50);

        DetectorColapso.Colapso colapso = detector.revisar(ejecucion, INICIO, INICIO).orElseThrow();

        assertEquals(DetectorColapso.Causa.PLAZO_INALCANZABLE, colapso.causa());
        assertEquals(1, colapso.vehiculosHabilitados());
    }

    @Test
    void soloSeRegistraElPrimerColapsoHastaQueLaEjecucionSeReinicia() {
        vehiculo("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE);
        pedido("P1", 30, 14);
        DetectorColapso.Colapso primero = detector.revisar(ejecucion, INICIO, PLAZO).orElseThrow();
        pedido("P2", 31, 14);

        assertEquals(primero, detector.revisar(ejecucion, INICIO, PLAZO.plusSeconds(7200)).orElseThrow());
        assertEquals(primero, detector.colapso(ejecucion).orElseThrow());

        detector.reiniciar(ejecucion);
        assertTrue(detector.colapso(ejecucion).isEmpty());
    }

    @Test
    void losPedidosAnterioresAlInicioDelEscenarioNoCuentan() {
        vehiculo("TA01", VehicleType.CAR, VehicleOperationalStatus.AVAILABLE);
        pedido("P1", 30, 14);

        assertTrue(detector.revisar(ejecucion, INICIO.plusSeconds(1), PLAZO.plusSeconds(3600)).isEmpty());
    }
}
