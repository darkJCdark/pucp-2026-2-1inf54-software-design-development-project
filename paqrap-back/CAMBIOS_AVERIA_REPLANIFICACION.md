# Corrección: fin de avería y replanificación

## Problema corregido

Una avería `MINOR` interrumpía la ruta y el estado interno del planificador dejaba a la unidad como `OUT_OF_SERVICE`. Aunque el monitoreo luego mostrara la unidad como disponible y la avería ya no estuviera activa, el planificador seguía excluyéndola de `vehiculosNoDisponibles`/nuevas rutas.

Además, el motor detectaba el inicio de una avería, pero no su final. Por eso no se disparaba una replanificación justo al terminar la indisponibilidad.

## Cambios

- `BreakdownResolution.availableFrom()` calcula el instante efectivo de recuperación.
- Una unidad con avería `MINOR` queda `AVAILABLE` con `availableAt = unavailableUntil`; por tanto, no puede planificarse durante las 2 horas y vuelve a ser elegible después.
- Las averías `INTERMEDIATE` y `MAJOR` quedan disponibles desde el mayor instante entre el fin de la indisponibilidad y el retorno al almacén central.
- `EventosSimulacion` detecta finalizaciones de averías en `(desde, hasta]`.
- `MotorEscenarios` trata el fin de avería como incidencia y solicita una replanificación inmediata.
- El evento de finalización se propaga por `PlanificadorPort`, `PlanificadorSaAdapter` y `ReplanificacionService`.
- Se agregaron/actualizaron pruebas de recuperación tras una avería menor.

## Comportamiento esperado

1. Se registra una avería `MINOR` sobre una unidad en ruta.
2. La ruta se interrumpe y lo pendiente se replanifica.
3. Durante 2 horas simuladas la unidad no es planificable.
4. Al terminar las 2 horas, el motor detecta el fin de la avería.
5. Se ejecuta una replanificación inmediata con motivo `AVERIA`.
6. La unidad deja de aparecer en `vehiculosNoDisponibles` y vuelve a ser candidata para nuevas rutas.

Que vuelva a ser candidata no garantiza que SA le asigne una ruta si no hay demanda pendiente o si otra unidad produce una solución mejor.

## Validación local

```powershell
mvn clean test
```

Después reinicia Spring y prueba con una **ejecución nueva**; las ejecuciones que ya estaban activas antes de la recompilación conservaban estado en memoria.
