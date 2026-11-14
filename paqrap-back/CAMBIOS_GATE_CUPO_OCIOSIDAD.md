# PaqRap — Ajuste conservador de urgencia, almacenes y flota ociosa

Fecha: 11 de noviembre de 2026. Base: el ZIP de posición efectiva y multi-almacén enviado en la conversación. **No modifica transferencias entre vehículos.**

## Motivo

En una captura FIVE_DAY (160×) se observaron 654 pedidos llegados, 135 entregados, 11 vencidos y 508 pendientes; el mapa mostraba 7 unidades alrededor del almacén Nor-Oeste y 3 en Este. Esa captura **no demuestra** que todos los vehículos estuvieran inactivos o que el almacén causara los vencimientos. Estas medidas son heurísticas conservadoras para mejorar la selección y generar evidencia en una simulación comparable.

## Archivos modificados

| Archivo | Cambio |
|---|---|
| `src/main/java/com/pucp/paqrap/modulos/planificacion/algoritmo/common/WarehouseSelectionPolicy.java` | Gate por urgencia y cap **blando** de terminales por almacén intermedio; cuenta unidades libres y retornos planificados, sin duplicar la unidad cuya ruta se está reemplazando |
| `src/main/java/com/pucp/paqrap/modulos/planificacion/algoritmo/alns/InsertionSupport.java` | Emergencias: para inserciones factibles, elige la llegada más temprana **antes** del costo; pasa snapshot/plan a la selección de terminal |
| `src/main/java/com/pucp/paqrap/modulos/planificacion/algoritmo/alns/VehicleSelectionPolicy.java` | Candidatos próximos al deadline se evalúan sin corte de top-K; reserva una plaza del ranking para un vehículo disponible y ocioso en un almacén intermedio (si su ETA es competitiva) |
| `src/main/java/com/pucp/paqrap/modulos/flota/persistence/VehicleEntity.java` | Método `finalizarRutaEn(Instant)` para guardar desde cuándo quedó disponible; no hay actualizaciones por tick |
| `src/main/java/com/pucp/paqrap/modulos/escenarios/service/EjecutorPlanService.java` | Al terminar una ruta, guarda `availableAt = llegadaFinal` y estado `AVAILABLE` |
| `src/test/java/com/pucp/paqrap/modulos/planificacion/algoritmo/common/WarehouseSelectionPolicyPressureTest.java` | 3 pruebas nuevas: cupo blando, excepción urgente y límite al desvío |
| `src/test/java/com/pucp/paqrap/modulos/planificacion/algoritmo/alns/VehicleIdleReactivationPolicyTest.java` | 1 prueba nueva: unidad ociosa entre los primeros candidatos |

## Parámetros iniciales

Los tres parámetros se encuentran al comienzo de las clases de política y **son puntos de partida para experimentación**, no valores optimizados:

- `VENTANA_URGENCIA = 6 horas`: pedidos con vencimiento dentro de esa ventana. En inserciones factibles, se favorece menor ETA antes de menor costo; para estos pedidos se examinan todos los vehículos candidatos, con el límite temporal del ciclo.
- `MAX_ESPERA_INTERMEDIO = 3`: límite *blando* de unidades libres o futuras terminaciones en un intermedio. **Nunca impide recargar ahí.** Si enviar una unidad a otra base aumenta el retorno en más de `DESVIO_MAXIMO_REPOSICION_KM`, se mantiene la base más cercana. No se crean estacionamientos ni movimientos adicionales para unidades ya paradas.
- `DESVIO_MAXIMO_REPOSICION_KM = 4 km`: máximo desvío extra aceptado para elegir otra base terminal.
- `UMBRAL_OCIOSIDAD = 30 min`: a partir de 30 min de disponibilidad, puede entrar un vehículo ocioso de un almacén intermedio en la segunda plaza del ranking, siempre que estimadamente cumpla SLA y no llegue más de 45 min después de la mejor opción.

Este diseño **no** crea una orden automática de salida sin pedido ni fuerza una redistribución física de paquetes. El vehículo sigue siendo candidato normal, y el evaluador de factibilidad sigue decidiendo si la ruta puede ejecutarse.

## Validaciones disponibles

En el entorno de edición sí se ejecutó una **compilación focalizada `javac --release 21`** de los tres archivos puros de planificación y se ejecutaron **13 métodos de prueba focalizados** mediante un runner Java mínimo (incluye 4 nuevos y 9 existentes). No se ha ejecutado Maven ni un Spring Boot integrado aquí (no están instaladas las dependencias Spring/JPA del proyecto en este entorno). Las modificaciones a `VehicleEntity` y `EjecutorPlanService` requieren Maven completo en tu máquina.

Ejecutar desde el directorio `paqrap-back`:

```powershell
mvn clean test '-Dtest=WarehouseSelectionPolicyPressureTest,VehicleIdleReactivationPolicyTest,WarehouseSelectionPolicyTest,VehicleSelectionPolicyTest'
mvn test '-Dtest=OperationalAlnsPlannerTest,OperationalAlnsMultiWarehouseTest,InsercionRutasActivasAlnsDesvioTest,ConfiguracionObjetivoPlanificacionTest,ConfiguracionAlnsEscenarioIntegrationTest'
mvn test
```

Si reaparece `NoClassDefFoundError: TramoRutaResponse`, revisar el classpath/compilación limpia. Si reaparece `NoSuchBeanDefinitionException: PlanificadorSaService`, obtener primero el **primer** `Caused by` anterior al error de inyección. Son defectos de integración detectados previamente y no pueden considerarse resueltos por este parche.

## Comparación necesaria

Evaluar el **mismo dataset, fecha, configuración, algoritmo y objetivo** que la corrida anterior, con especial atención a comparaciones al **mismo instante simulado** (por ejemplo 2028-12-01 13:15). Registrar:

1. Llegados, entregados a tiempo, pendientes y vencidos.
2. Vehículos `AVAILABLE` vs `IN_ROUTE`, separando ubicación efectiva por almacén.
3. Replanes y pedidos que permanecieron sin asignar aun cuando existían vehículos libres.
4. Visitas y stock consumido por almacén, costo y km.
5. Errores de validación de stock, retornos y plazos.

**Regla para aceptar la mejora:** a igual carga temporal, no empeorar pedidos a tiempo; entre configuraciones con servicio comparable, comparar costo y distancia. Un porcentaje menor de vehículos en Nor-Oeste, por sí solo, no demuestra mejor atención.

## Instalación

Preferir el archivo `PAQRAP_GATE_CUPO_OCIOSIDAD.patch` aplicado con Git en una rama aparte en el `paqrap-back` actual, para **no sobrescribir** cambios posteriores:

```powershell
git switch -c fix/gate-cupo-ociosidad
git apply --check PAQRAP_GATE_CUPO_OCIOSIDAD.patch
git apply PAQRAP_GATE_CUPO_OCIOSIDAD.patch
```

Alternativa: descomprimir el ZIP completo en una carpeta nueva y revisar diferencias antes de copiar sobre tu proyecto.
