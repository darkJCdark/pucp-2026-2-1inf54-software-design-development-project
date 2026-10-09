package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.InicializadorFlota;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuenteDatosOperativosMockTest {

    private final FuenteDatosOperativos fuente = new FuenteDatosOperativosMock();

    private static long contar(List<Vehicle> flota, VehicleType tipo) {
        return flota.stream().filter(v -> v.type() == tipo).count();
    }

    @Test
    void laFlotaEsLaMismaQueInicializadorFlota() {
        List<Vehicle> esperada = InicializadorFlota.crearFlotaInicial();

        assertEquals(esperada, fuente.flota());
    }

    @Test
    void laFlotaMantieneLaCantidadPorTipoDeInicializadorFlota() {
        List<Vehicle> esperada = InicializadorFlota.crearFlotaInicial();
        List<Vehicle> flota = fuente.flota();

        assertEquals(esperada.size(), flota.size());
        for (VehicleType tipo : VehicleType.values()) {
            assertEquals(contar(esperada, tipo), contar(flota, tipo));
        }
    }

    @Test
    void cadaLlamadaEntregaUnaFlotaIndependiente() {
        List<Vehicle> primera = fuente.flota();
        List<Vehicle> segunda = fuente.flota();

        assertEquals(primera, segunda);
        primera.clear();
        assertFalse(fuente.flota().isEmpty());
    }

    @Test
    void definePorLoMenosUnAlmacenCentralYUnicoParaElSnapshot() {
        Collection<Warehouse> almacenes = fuente.almacenes();

        assertEquals(1, almacenes.stream().filter(Warehouse::isCentral).count());
    }

    @Test
    void losIdsDeAlmacenSonUnicos() {
        Set<String> ids = new HashSet<>();
        for (Warehouse almacen : fuente.almacenes()) {
            assertTrue(ids.add(almacen.id()), "id repetido: " + almacen.id());
        }
    }

    /** Estos valores los definí yo para el mock (no vienen del código original). */
    @Test
    void almacenesDelMockTienenLasUbicacionesDefinidas() {
        Warehouse central = fuente.almacenes().stream().filter(Warehouse::isCentral).findFirst().orElseThrow();

        assertEquals("CENTRAL", central.id());
        assertEquals(new Location(27, 14), central.location());
        assertEquals(new Location(12, 38), ubicacionDe("NOROESTE"));
        assertEquals(new Location(57, 27), ubicacionDe("ESTE"));
    }

    @Test
    void losIntermediosTienenStockIgualAsuCapacidadOriginal() {
        for (Warehouse almacen : fuente.almacenes()) {
            if (!almacen.isCentral()) {
                assertEquals(almacen.capacity(), almacen.initialStock());
            }
        }
    }

    @Test
    void inventorySnapshotAceptaLosAlmacenesYReportaSuStock() {
        InventorySnapshot inventario = InventorySnapshot.from(fuente.almacenes());

        assertEquals(fuente.almacenes().size(), inventario.warehouses().size());
        for (Warehouse almacen : fuente.almacenes()) {
            assertEquals(almacen.isCentral() ? Integer.MAX_VALUE : almacen.initialStock(),
                    inventario.availableStock(almacen.id()));
        }
    }

    @Test
    void noHayMantenimientosNiAverias() {
        Instant instante = Instant.parse("2026-09-09T12:00:00Z");

        assertTrue(fuente.averias().isEmpty());
        for (Vehicle vehiculo : fuente.flota()) {
            assertFalse(fuente.calendarioMantenimiento().isUnavailable(vehiculo.id(), instante));
        }
    }

    @Test
    void elPerfilDeFlotaCoincideConLosParametrosOriginalesDeCadaTipo() {
        FleetProfile perfil = fuente.perfilFlota();

        for (VehicleType tipo : VehicleType.values()) {
            assertEquals(tipo.defaultParameters(), perfil.parametersFor(tipo));
            assertEquals(FleetProfile.defaults().parametersFor(tipo), perfil.parametersFor(tipo));
        }
    }

    private Location ubicacionDe(String idAlmacen) {
        return fuente.almacenes().stream()
                .filter(almacen -> almacen.id().equals(idAlmacen))
                .findFirst().orElseThrow().location();
    }
}
