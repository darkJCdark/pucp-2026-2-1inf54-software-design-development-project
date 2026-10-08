package com.pucp.paqrap.modulos.flota.repository;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleTypeParametersRepository extends JpaRepository<VehicleTypeParametersEntity, VehicleType> {
}
