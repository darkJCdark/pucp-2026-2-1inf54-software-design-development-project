# Diagnóstico técnico: posición efectiva, multi-almacén y ALNS

**Proyecto:** PaqRap backend  
**Fecha:** 11 de noviembre de 2026  
**Alcance de esta entrega:** corregir el estado espacial de la flota durante la replanificación, aprovechar los almacenes intermedios y ampliar ALNS sin introducir transferencia física de pedidos entre vehículos.

> Nota de interpretación: la solicitud decía “usar más los almacenes centrales”. En el contexto del proyecto, el almacén Central ya era el punto sobreutilizado; por ello, esta implementación busca usar más **Nor-Oeste** y **Este**, respetando que la primera salida de cada vehículo siga ocurriendo desde Central.

---

## 1. Diagnóstico previo

### 1.1. Posición incorrecta de vehículos en replanificación

El backend tenía dos representaciones distintas de la posición:

- el frontend animaba el vehículo con la ruta y el porcentaje de avance;
- `VehicleEntity.currentX/currentY` conservaba el último checkpoint persistido;
- `ReplanificacionSaService` construía el snapshot de una ruta activa con:

```text
estado = IN_ROUTE
posición = almacén Central
carga = 0
```

Por tanto, un vehículo que visualmente estaba en otra zona podía entrar a la planificación como si continuara en `(27,14)`. Esto afectaba potencialmente:

- selección del vehículo más cercano;
- estimación de llegada y holgura;
- inserción de pedidos urgentes;
- costo y distancia marginales;
- decisiones de desvío;
- distribución espacial de la flota.

### 1.2. Monitoreo con posición persistida

`MonitoreoService` y `DetalleOperacionService` exponían directamente la coordenada persistida. El frontend podía dibujar el movimiento, pero el panel y la API seguían mostrando el último nodo confirmado.

### 1.3. Sesgo estructural hacia Central

Las rutas nuevas, recargas y retornos se construían alrededor del almacén Central. Aunque existían almacenes en:

```text
Central:    (27,14)
Nor-Oeste:  (12,38)
Este:       (57,27)
```

la búsqueda no comparaba sistemáticamente:

```text
vehículo → almacén → pedido
```

entre los tres puntos de abastecimiento.

### 1.4. ALNS todavía tenía poca diversidad

El pool original usaba:

```text
Destroy:
- random-route-removal
- worst-route-removal

Repair:
- cheapest-insertion
- regret-2-insertion
```

Faltaban dos extensiones que ya se habían identificado como útiles:

- remoción relacionada o tipo Shaw;
- inserción Regret-3.

---

## 2. Cambios implementados

## 2.1. Estado físico efectivo de una ruta activa

Se añadió:

```text
EstadoVehiculoRutaService
```

Esta clase deriva el estado desde la programación real de la ruta:

- localiza el vehículo sobre los `RoadLeg`;
- si está en mitad de una calle, utiliza el siguiente nodo seguro;
- si está detenido, utiliza el nodo actual;
- calcula la carga física a partir de `loadBefore/loadAfter`;
- conserva `availableAt` hasta el final de la ruta para impedir que se abra una segunda ruta simultánea.

El snapshot de replanificación ahora sigue este orden:

```text
1. estado forzado por avería o retorno explícito;
2. estado derivado de la ruta activa;
3. último estado persistido del vehículo libre;
4. Central como fallback excepcional.
```

Ya no se asigna Central y carga cero a todas las rutas activas.

## 2.2. Posición coherente en monitoreo

Para vehículos `IN_ROUTE`, ahora:

```text
MonitoreoService
DetalleOperacionService
```

consultan:

```text
EjecutorPlanService.posicionEstimada(...)
```

La coordenada persistida queda como fallback cuando no existe una ruta ejecutándose.

Resultado esperado:

```text
mapa animado ≈ API de monitoreo ≈ detalle del vehículo
```

La interfaz puede seguir interpolando suavemente, mientras el backend expone el nodo estimado correspondiente al instante simulado.

## 2.3. Estado persistido de vehículos libres

`FuenteDatosOperativos` incorpora:

```text
estadosFlota(Instant)
```

y `FuenteDatosOperativosBd` lo implementa desde `VehicleEntity`.

Esto permite que un vehículo que terminó su ruta en Este o Nor-Oeste vuelva a planificarse desde ese punto y no reaparezca automáticamente en Central.

## 2.4. Política compartida de almacenes

Se añadió:

```text
WarehouseSelectionPolicy
```

Para abastecimiento ordena almacenes con stock suficiente mediante:

```text
distancia Manhattan:
origen → almacén → pedido
```

En empates exactos favorece un almacén intermedio.

Para el cierre de ruta selecciona el almacén más cercano a la última entrega, también favoreciendo un intermedio en empate.

Las restricciones duras siguen bajo `OperationalPlanEvaluator`; la política solo ordena candidatos.

## 2.5. Rutas nuevas multi-almacén

`InsertionSupport` ahora:

- mantiene Central como origen de la primera salida del escenario;
- para viajes posteriores selecciona el almacén de abastecimiento más conveniente;
- devuelve el vehículo al almacén más cercano tras la última entrega;
- descuenta las reservas ya incluidas en el plan para no reutilizar ficticiamente el mismo stock;
- conserva el inventario infinito del Central.

## 2.6. Selección de vehículos consciente del almacén

`VehicleSelectionPolicy` ahora estima cada alternativa usando:

```text
posición del vehículo
→ almacén factible
→ destino
→ almacén terminal
```

en vez de asumir que todos los viajes pasan por Central.

La validación exacta del plan sigue a cargo del evaluador.

## 2.7. Inserción en rutas activas con almacenes intermedios

`InsercionRutasActivasAlns` conserva su firma anterior para compatibilidad, pero añade una sobrecarga con `InventorySnapshot`.

En inserciones normales sobre una ruta activa:

- evalúa todos los almacenes con stock suficiente;
- inserta recarga + entrega + retorno;
- mantiene una reserva global del stock aceptado durante el mismo ciclo;
- evita asignar dos veces el inventario de Este o Nor-Oeste.

Las rutas críticas todavía conservan parte de la estrategia de emergencia basada en Central. Esto se dejó deliberadamente como siguiente iteración para no mezclar demasiadas reglas en un solo cambio.

## 2.8. Nuevos operadores ALNS

### `RelatedRouteRemoval`

Remoción relacionada conservadora a nivel de ruta. Calcula semejanza mediante:

- centroides geográficos de entregas;
- deadline más próximo;
- volumen de paquetes.

Se retiran rutas completas para no romper la consistencia de entregas parciales.

Nombre del operador:

```text
related-route-removal
```

### `Regret3InsertionRepair`

Prioriza demandas cuya mejor alternativa es mucho mejor que la segunda y tercera.

Cuando existen menos de tres opciones factibles, la demanda recibe prioridad por ser restringida.

Nombre del operador:

```text
regret-3-insertion
```

### Pool ALNS resultante

```text
Destroy:
- random-route-removal
- worst-route-removal
- related-route-removal

Repair:
- cheapest-insertion
- regret-2-insertion
- regret-3-insertion
```

Los pesos continúan adaptándose con el mecanismo existente.

---

## 3. Funcionalidad pospuesta deliberadamente

No se implementó transferencia física de paquetes o pedidos entre vehículos que se cruzan.

También se dejó fuera de esta entrega:

- precarga o stock móvil;
- intercambio de carga vehículo a vehículo;
- transferencia de un pedido ya cargado;
- sincronización de dos vehículos en un nodo;
- costo/tiempo de transbordo;
- versión equivalente de estas reglas para SA;
- `RelatedRemoval` a nivel de pedido individual;
- política exacta de rutas alternativas por bloqueo para cada almacén.

Esta decisión mantiene el modelo operativo auditable y evita introducir simultáneamente coordinación temporal, inventario móvil y traspasos físicos.

---

## 4. Archivos modificados

### Nuevos en producción

```text
src/main/java/com/pucp/paqrap/modulos/planificacion/algoritmo/common/WarehouseSelectionPolicy.java
src/main/java/com/pucp/paqrap/modulos/planificacion/service/EstadoVehiculoRutaService.java
src/main/java/com/pucp/paqrap/modulos/planificacion/algoritmo/alns/RelatedRouteRemoval.java
src/main/java/com/pucp/paqrap/modulos/planificacion/algoritmo/alns/Regret3InsertionRepair.java
```

### Modificados en producción

```text
src/main/java/com/pucp/paqrap/modulos/planificacion/service/FuenteDatosOperativos.java
src/main/java/com/pucp/paqrap/modulos/planificacion/service/FuenteDatosOperativosBd.java
src/main/java/com/pucp/paqrap/modulos/planificacion/service/ReplanificacionSaService.java
src/main/java/com/pucp/paqrap/modulos/planificacion/service/InsercionRutasActivasAlns.java
src/main/java/com/pucp/paqrap/modulos/planificacion/algoritmo/alns/InsertionSupport.java
src/main/java/com/pucp/paqrap/modulos/planificacion/algoritmo/alns/VehicleSelectionPolicy.java
src/main/java/com/pucp/paqrap/modulos/planificacion/algoritmo/alns/OperationalAlnsPlanner.java
src/main/java/com/pucp/paqrap/modulos/monitoreo/service/MonitoreoService.java
src/main/java/com/pucp/paqrap/modulos/monitoreo/service/DetalleOperacionService.java
```

### Pruebas nuevas

```text
src/test/java/com/pucp/paqrap/modulos/planificacion/algoritmo/common/WarehouseSelectionPolicyTest.java
src/test/java/com/pucp/paqrap/modulos/planificacion/algoritmo/alns/OperationalAlnsMultiWarehouseTest.java
src/test/java/com/pucp/paqrap/modulos/planificacion/service/EstadoVehiculoRutaServiceTest.java
src/test/java/com/pucp/paqrap/modulos/planificacion/service/InsercionRutasActivasMultiWarehouseTest.java
```

---

## 5. Validación realizada en este entorno

En el entorno de edición no estaba instalado Maven ni estaban disponibles las dependencias remotas. Por ello se realizaron validaciones con Java 21, las clases ya compiladas del ZIP y stubs mínimos de Spring/JUnit.

### Compilación

```text
13 archivos principales modificados/nuevos: OK
4 pruebas nuevas: OK
```

Las advertencias de `jakarta.persistence` se deben únicamente a que el classpath local de validación no contenía las dependencias Maven; no fueron errores de compilación.

### Pruebas ejecutables de humo

```text
WAREHOUSE_POLICY_OK supply=EAST terminal=EAST

ALNS_MULTIWAREHOUSE_OK
terminalEast=true
pickupEast=true
destroy=[related-route-removal, random-route-removal, worst-route-removal]
repair=[regret-3-insertion, cheapest-insertion, regret-2-insertion]

POSITION_STATE_OK
location=28,14
load=7
availableAt=2028-12-01T02:24:00Z

ACTIVE_MULTIWAREHOUSE_OK
stops=4
```

Estas pruebas verificaron:

- selección de Este para pedido cercano;
- retorno a Este;
- presencia de los nuevos operadores;
- posición distinta de Central durante un tramo;
- conservación de carga;
- inserción multi-almacén en una ruta activa.

### Validación todavía obligatoria en la máquina del proyecto

Debe ejecutarse la suite Maven completa. Se incluye:

```text
VALIDAR_CAMBIOS_ALNS.ps1
```

---

## 6. Cómo validar localmente

Desde `paqrap-back`:

```powershell
.\VALIDAR_CAMBIOS_ALNS.ps1
```

O manualmente:

```powershell
mvn clean test "-Dtest=WarehouseSelectionPolicyTest,OperationalAlnsMultiWarehouseTest,EstadoVehiculoRutaServiceTest,InsercionRutasActivasMultiWarehouseTest"
```

Luego:

```powershell
mvn test "-Dtest=OperationalAlnsPlannerTest,OperationalAlnsObjectivePlannerTest,ServiceFirstSeedRescueTest,VehicleSelectionPolicyTest,InsercionRutasActivasAlnsDesvioTest,UrgencyRemovalAlnsServiceTest,RedistribucionEntreVehiculosAlnsTest,LocalizadorRutaServiceTest,ContinuacionRutaServiceTest,ConfiguracionAlnsEscenarioIntegrationTest,ConfiguracionObjetivoPlanificacionTest"
```

Finalmente:

```powershell
mvn test
```

---

## 7. Prueba visual y operacional recomendada

Ejecutar el mismo FIVE_DAY con:

```powershell
$env:PAQRAP_PLAN_ALGORITMO="ALNS"
$env:PAQRAP_PLAN_OBJETIVO="SERVICE_MAX"
mvn spring-boot:run
```

Usar exactamente:

- mismo mes;
- mismo inicio simulado;
- misma semilla;
- misma flota;
- mismos bloqueos;
- mismo presupuesto;
- mismos parámetros ALNS.

Registrar en al menos cuatro cortes:

```text
+6 h
+12 h
+24 h
fin de los 5 días
```

Métricas:

```text
llegados
entregados
vencidos
pendientes
costo
distancia
rutas
replanes
stock Este
stock Nor-Oeste
visitas por almacén
vehículos cuya ruta termina en cada almacén
```

Evidencias visuales esperadas:

1. Un vehículo `IN_ROUTE` ya no aparece permanentemente en `(27,14)` en el panel.
2. El nivel conjunto de almacenes intermedios debe bajar de `2000/2000` cuando se usen.
3. Deben aparecer recargas y cierres de ruta en Este y Nor-Oeste.
4. La flota no debe concentrarse automáticamente en Central después de cada ciclo.
5. No debe existir una segunda ruta simultánea para el mismo vehículo.

---

## 8. Riesgos y límites conocidos

### 8.1. Ranking por distancia Manhattan

La política usa una aproximación barata para ordenar almacenes. El evaluador comprueba la factibilidad real, incluidos bloqueos, pero en rutas nuevas se construye primero con el mejor almacén heurístico.

Siguiente mejora recomendable:

```text
generar candidatos:
(vehículo, almacén, posición de inserción)
```

y evaluar varias alternativas exactas antes de descartarlas.

### 8.2. Emergencias todavía orientadas a Central

La inserción normal usa los almacenes intermedios. Algunas rutas de rescate crítico todavía recargan en Central para conservar un cambio acotado.

Debe extenderse después de validar este baseline.

### 8.3. SA todavía no tiene paridad

Las reglas nuevas están integradas en ALNS. Una comparación estadística SA vs ALNS todavía no sería completamente justa.

Antes de concluir superioridad algorítmica hay que compartir con SA:

- posición efectiva;
- política de almacenes;
- misma prioridad SLA;
- misma inserción en rutas activas;
- mismas reglas de refrigerio.

### 8.4. Reposición diaria

Los almacenes intermedios se reponen según la lógica existente. Conviene añadir una prueba específica que cruce las 23:59:59 con reservas y rutas futuras.

### 8.5. Ajuste de operadores

Los operadores nuevos están incorporados, pero sus pesos y rendimiento deben medirse. No se deben asignar pesos manuales solo por intuición.

Registrar por operador:

```text
selecciones
candidatos factibles
aceptaciones
mejoras globales
reducción de no atendidos
reducción de costo con servicio constante
tiempo de cómputo
```

---

## 9. Estado actual del algoritmo

Después de esta entrega, ALNS queda con:

```text
✓ objetivo SERVICE_MAX / SERVICE_THEN_COST
✓ rescate de no atendidos
✓ rutas activas y checkpoints
✓ desvío crítico
✓ remoción por urgencia
✓ posición efectiva en snapshot
✓ carga efectiva de ruta
✓ monitoreo coherente con la ruta
✓ multi-almacén en rutas nuevas
✓ multi-almacén en inserciones normales sobre rutas activas
✓ reserva de stock intermedio
✓ RelatedRouteRemoval
✓ Regret-3
```

Pendiente:

```text
△ Maven completo en la máquina del proyecto
△ baseline FIVE_DAY controlado
△ multi-almacén en todos los caminos críticos
△ ranking exacto considerando bloqueos
△ telemetría por operador
△ paridad de reglas con SA
△ tuning reproducible
△ precarga o stock móvil
× transferencia vehículo a vehículo, pospuesta
```

---

## 10. Orden recomendado para continuar

### Etapa 1 — Validación inmediata

1. Ejecutar `VALIDAR_CAMBIOS_ALNS.ps1`.
2. Corregir cualquier incompatibilidad revelada por la suite completa.
3. Hacer una corrida FIVE_DAY corta y comprobar posición/stock.

### Etapa 2 — Baseline reproducible

1. Ejecutar ALNS `SERVICE_MAX` con la versión anterior.
2. Ejecutar la versión actual con la misma semilla.
3. Comparar servicio, costo, distancia y uso de almacenes.
4. Repetir con varias semillas.

### Etapa 3 — Completar multi-almacén

1. Probar varios almacenes exactos cuando el primero falla por bloqueo.
2. Llevar la política a los caminos de desvío crítico.
3. Añadir prueba de reposición diaria.
4. Exponer métricas de salidas/recargas por almacén.

### Etapa 4 — Pulir ALNS con evidencia

1. Medir contribución de cada destroy/repair.
2. Comparar:
   - sin Related;
   - con Related;
   - Regret-2;
   - Regret-3.
3. Ajustar `reactionFactor`, tamaño destroy y presupuesto.
4. Mantener configuración ganadora solo si mejora resultados repetidos.

### Etapa 5 — Comparación justa con SA

Portar las mismas reglas operativas a SA y ejecutar un benchmark pareado.

### Etapa 6 — Nuevas capacidades

Después:

- precarga controlada;
- inventario móvil;
- stock de seguridad;
- solo al final, evaluar transferencias entre vehículos.

---

## 11. Mensaje de commit sugerido

```text
feat(planning): use effective vehicle positions and multi-warehouse ALNS

- derive active vehicle position and load from scheduled routes
- expose estimated in-route positions in monitoring and detail APIs
- preserve persisted location for available vehicles
- select intermediate warehouses for reloads and route termination
- reserve intermediate stock across active and new routes
- add related-route removal and regret-3 repair operators
- add focused tests and validation script
- keep vehicle-to-vehicle transfers out of scope
```
