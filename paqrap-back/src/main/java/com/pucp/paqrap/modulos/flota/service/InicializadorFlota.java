package com.pucp.paqrap.modulos.flota.service;
import com.pucp.paqrap.modulos.almacenes.entity.*;
import com.pucp.paqrap.modulos.flota.entity.*;
import com.pucp.paqrap.modulos.flota.service.*;
import com.pucp.paqrap.modulos.incidencias.entity.*;
import com.pucp.paqrap.modulos.incidencias.service.*;
import com.pucp.paqrap.modulos.pedidos.entity.*;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.*;
import com.pucp.paqrap.modulos.planificacion.entity.*;
import com.pucp.paqrap.modulos.redvial.entity.*;
import com.pucp.paqrap.modulos.redvial.service.*;

import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;

import java.util.ArrayList;
import java.util.List;

/** Crea las 37 unidades reales de la flota (hoja "Flota" del Excel de preguntas y respuestas). */
public class InicializadorFlota {
    public static List<Vehicle> crearFlotaInicial() {
        List<Vehicle> flota = new ArrayList<>();
        for (int i = 1; i <= 10; i++) flota.add(new Vehicle(String.format("TA%02d", i), VehicleType.CAR, true));
        for (int i = 1; i <= 15; i++) flota.add(new Vehicle(String.format("TM%02d", i), VehicleType.MOTORCYCLE, true));
        for (int i = 1; i <= 12; i++) flota.add(new Vehicle(String.format("TB%02d", i), VehicleType.BICYCLE, true));
        return flota; // 10 + 15 + 12 = 37 (hoja "Flota" del Excel de Preguntas y Respuestas)
    }
}
