package com.pucp.paqrap.modulos.almacenes.controller;

import com.pucp.paqrap.modulos.almacenes.dto.AlmacenResponse;
import com.pucp.paqrap.modulos.almacenes.service.AlmacenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/almacenes")
public class AlmacenController {

    private final AlmacenService almacenService;

    public AlmacenController(AlmacenService almacenService) {
        this.almacenService = almacenService;
    }

    @GetMapping
    public List<AlmacenResponse> listar() {
        return almacenService.listar();
    }

    @GetMapping("/{id}")
    public AlmacenResponse obtener(@PathVariable String id) {
        return almacenService.obtener(id);
    }
}
