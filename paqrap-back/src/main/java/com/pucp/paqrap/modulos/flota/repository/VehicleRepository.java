package com.pucp.paqrap.modulos.flota.repository;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VehicleRepository extends JpaRepository<VehicleEntity, String> {

    @Query("""
            select v from VehicleEntity v
            where (:tipo is null or v.vehicleType = :tipo)
              and (:estado is null or v.operationalStatus = :estado)
            order by v.vehicleId
            """)
    List<VehicleEntity> buscar(@Param("tipo") VehicleType tipo, @Param("estado") VehicleOperationalStatus estado);

    /**
     * 1 si la unidad aparece en alguna tabla cuya llave foránea impide borrarla (ON DELETE RESTRICT): rutas, averías o
     * días de mantenimiento. El historial de estados de pedidos usa ON DELETE SET NULL y no bloquea la baja.
     */
    @Query(value = """
            select case when exists (select 1 from routes r where r.vehicle_id = :id)
                          or exists (select 1 from breakdown_events b where b.vehicle_id = :id)
                          or exists (select 1 from maintenance_days m where m.vehicle_id = :id)
                        then 1 else 0 end
            """, nativeQuery = true)
    int tieneHistorial(@Param("id") String id);
}
