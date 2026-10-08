package com.pucp.paqrap.modulos.flota.repository;

import com.pucp.paqrap.modulos.flota.persistence.MaintenanceDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MaintenanceDayRepository extends JpaRepository<MaintenanceDayEntity, MaintenanceDayEntity.Id> {

    @Query("""
            select m from MaintenanceDayEntity m
            where (:vehiculoId is null or m.vehicleId = :vehiculoId)
              and (:desde is null or m.maintenanceDate >= :desde)
              and (:hasta is null or m.maintenanceDate <= :hasta)
            order by m.maintenanceDate, m.vehicleId
            """)
    List<MaintenanceDayEntity> buscar(@Param("vehiculoId") String vehiculoId,
                                      @Param("desde") LocalDate desde,
                                      @Param("hasta") LocalDate hasta);
}
