package pe.pucp.paqrap.modelo;

import pe.edu.pucp.paqrap.planner.domain.FleetProfile;
import pe.edu.pucp.paqrap.planner.domain.VehicleParameters;
import pe.edu.pucp.paqrap.planner.domain.VehicleType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * Carga la velocidad por tipo de vehiculo desde un archivo .properties
 * externo y construye un FleetProfile explicito.
 *
 * Deliberadamente NO se usa FleetProfile.defaults(): ese metodo toma la
 * velocidad "por defecto" de VehicleType (situacion autentica: 40/25/12),
 * lo que resuelve en silencio el conflicto sin resolver con la hoja
 * "Flota" (autos=20, motos=40, bicicletas=14). Este cargador mantiene
 * agnostico el back -- no hay velocidad hasta que se inyecta explicitamente
 * un archivo.
 */
public class CargadorParametros {
    public static FleetProfile desdeArchivo(Path archivo) throws IOException {
        Properties props = new Properties();
        try (var in = Files.newInputStream(archivo)) { props.load(in); }

        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        for (VehicleType tipo : VehicleType.values()) {
            String clave = "velocidad." + claveEnEspanol(tipo);
            double velocidad = Double.parseDouble(requireProperty(props, clave));
            VehicleParameters base = tipo.defaultParameters();
            parametros.put(tipo, new VehicleParameters(base.capacity(), velocidad, base.costPerKm()));
        }
        return new FleetProfile(parametros);
    }

    private static String claveEnEspanol(VehicleType tipo) {
        return switch (tipo) {
            case CAR -> "auto";
            case MOTORCYCLE -> "moto";
            case BICYCLE -> "bicicleta";
        };
    }

    private static String requireProperty(Properties props, String clave) {
        String valor = props.getProperty(clave);
        if (valor == null) {
            throw new IllegalArgumentException("Falta la propiedad '" + clave + "' en el archivo de velocidades");
        }
        return valor;
    }
}
