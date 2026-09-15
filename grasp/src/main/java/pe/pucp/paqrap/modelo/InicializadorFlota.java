package pe.pucp.paqrap.modelo;

import pe.edu.pucp.paqrap.planner.domain.Vehicle;
import pe.edu.pucp.paqrap.planner.domain.VehicleType;

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
