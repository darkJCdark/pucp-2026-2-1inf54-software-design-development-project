package com.pucp.paqrap.modulos.almacenes.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.modulos.almacenes.dto.AlmacenResponse;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AlmacenService {

    private final WarehouseRepository almacenRepository;

    public AlmacenService(WarehouseRepository almacenRepository) {
        this.almacenRepository = almacenRepository;
    }

    public List<AlmacenResponse> listar() {
        return almacenRepository.findAll(Sort.by("warehouseKind", "warehouseId")).stream()
                .map(AlmacenResponse::de)
                .toList();
    }

    public AlmacenResponse obtener(String id) {
        return almacenRepository.findById(id)
                .map(AlmacenResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Almacén", id));
    }
}
