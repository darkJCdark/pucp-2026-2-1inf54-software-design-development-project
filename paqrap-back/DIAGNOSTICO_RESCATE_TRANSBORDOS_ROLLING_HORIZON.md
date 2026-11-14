# Diagnóstico técnico final — PaqRap

**Base utilizada:** `paqrap-back-gate-cupo-ociosidad(1).zip`  
**Alcance:** backend Java 21/Spring Boot 3.5; no incluye frontend.  
**Objetivo:** rescate preventivo, transbordos físicos, política multi-almacén moderada y rolling horizon preventivo.

## 1. Resultado de la implementación

La nueva arquitectura separa dos escalas de decisión:

```text
ALNS / SA
  → optimización global periódica y por eventos

Rescate preventivo
  → corrección local dirigida por ETA y slack
```

Esto evita esperar que un operador aleatorio de ALNS descubra una reasignación urgente dentro de un presupuesto corto.

### 1.1. Rescate sin carga física

Si la entrega riesgosa todavía está precedida por una recarga futura específica, el sistema:

1. elimina la pareja recarga + entrega del vehículo original;
2. reconstruye de forma físicamente continua la ruta original;
3. prueba inserción en las otras rutas activas;
4. recalcula ambas programaciones;
5. acepta solo si la ETA mejora al menos 15 minutos y no crea nuevos incumplimientos.

### 1.2. Rescate con carga física

Si los paquetes ya están a bordo, el pedido no se retira virtualmente. Se construye una pareja de `TransferStop`:

```text
vehículo donante ── DONATE ─┐
                            ├─ mismo nodo y misma hora
vehículo receptor ─ RECEIVE ┘
```

El receptor continúa con la entrega. El plan se rechaza si no conserva carga, capacidad, sincronización, deadlines, refrigerio, averías y continuidad física.

### 1.3. Ejecución y recuperación

El ejecutor aplica la carga una sola vez al iniciar el servicio del encuentro. Una propuesta de plan no altera anticipadamente la carga persistida.

Si el encuentro no ocurre:

- se cancela de forma idempotente;
- se registra un `FalloTransbordoOperativo`;
- el pedido sigue perteneciendo al donante;
- se reconstruyen las rutas;
- si no es posible hacerlo en ese ciclo, se bloquean ambos participantes y se protegen todos sus compromisos futuros para impedir asignaciones duplicadas.

### 1.4. Política multi-almacén moderada

El problema previo no era usar almacenes intermedios, sino convertirlos en destinos por defecto. La política actual:

- conserva Central como ganador de empates;
- exige ahorro material de 3 km para cerrar en un intermedio;
- reemplaza el cupo duro por penalización gradual;
- no genera viajes vacíos para redistribuir unidades;
- penaliza stock intermedio bajo;
- permite saltar el sesgo de ocupación ante urgencia;
- reutiliza carga residual en vehículos disponibles.

### 1.5. Rolling horizon

El sistema mantiene:

- optimización global aproximadamente cada hora simulada;
- replanificación inmediata por pedidos, averías y bloqueos;
- revisión ligera de ETA cada 15 minutos;
- reactivación por fin de ruta;
- recuperación inmediata por fallo de transbordo.

La revisión ligera consulta en lote los pedidos de las entregas comprometidas. Si no hay riesgo, no invoca ALNS. Si un rescate local resuelve el riesgo, tampoco desplaza la siguiente optimización global.

## 2. Componentes principales incorporados

### Dominio y programación

- `TransferDirection`
- `TransferStop`
- soporte de espera en `ScheduledRouteStop`
- sincronización y cambio de carga en `RouteScheduler`
- validaciones de pareja/sincronización en `OperationalPlanEvaluator`

### Rescate y recuperación

- `RescatePreventivoProperties`
- `RescatePreventivoPedidosService`
- `FalloTransbordoOperativo`
- `RecuperacionTransbordoFallidoService`

### Ejecución y persistencia

- transbordos en `EjecutorPlanService`
- campos de transferencia en `ParadaResponse`
- mapeo en `PlanResponseMapper`
- persistencia en `RouteStopEntity` y `PlanPersistenceService`
- migración `paqrap_migration_v2_vehicle_transfers.sql`

### Rolling horizon

- parámetros nuevos en `EscenariosProperties`
- disparadores `RIESGO_ETA`, `FIN_RUTA`, `TRANSBORDO_FALLIDO`
- escaneo barato en `MotorEscenarios`
- propagación de fallos por `PlanificadorPort`, `PlanificadorSaAdapter` y `ReplanificacionService`

## 3. Validación realizada

### Comprobaciones ejecutadas

- compilación focalizada de todos los archivos de producción modificados con Java 21;
- 45 pruebas focalizadas únicas sin fallos;
- conservación y sincronización de carga;
- rechazo de transbordo sin contraparte;
- rescate de pedido no cargado;
- rescate de pedido cargado;
- desactivación de transbordos;
- recuperación física tras encuentro cancelado;
- protección de compromisos de ambos vehículos cuando la recuperación queda pendiente;
- carga correcta antes de iniciar el encuentro;
- política moderada de almacenes;
- reutilización de carga residual;
- prioridad costo/ETA para alternativas SLA-factibles;
- mapeo completo del transbordo al DTO;
- regresiones focalizadas de continuidad, localización, refrigerio e inserción multi-almacén.

### Validación no ejecutada en este entorno

Maven no estaba disponible. Por ello no se afirma todavía:

- suite completa Surefire verde;
- contexto Spring completo verde;
- migración probada sobre una copia real de MySQL;
- corrida FIVE_DAY estable;
- reducción empírica de vencidos.

La entrega incluye un script para efectuar esas comprobaciones localmente.

## 4. Hallazgos adicionales durante la revisión

### 4.1. Vehículos con carga residual eran descartados

`VehicleSelectionPolicy` excluía unidades disponibles cuya carga fuera distinta de cero. Esto podía dejar vehículos con paquetes inmovilizados en almacenes. Ahora pueden:

- ir directamente al cliente si llevan cantidad suficiente;
- recoger únicamente la cantidad faltante;
- conservar la capacidad y carga proyectada correctas.

### 4.2. El criterio urgente había quedado invertido

Una rama priorizaba ETA incluso después de comprobar que varias opciones cumplían el SLA, lo que podía seleccionar una moto costosa frente a una bicicleta también puntual. Ahora:

1. se filtra factibilidad SLA;
2. entre alternativas puntuales domina costo;
3. ETA desempata.

### 4.3. `DetectorColapso` era incompatible con multi-almacén

La cota anterior dependía de Central y revisaba solo `REGISTERED`. Ahora considera:

- todos los pedidos no entregados y aún vigentes;
- almacenes alternativos;
- posiciones reales de vehículos habilitados.

Sigue siendo una cota optimista, no una prueba exacta de inviabilidad de VRP dinámico.

## 5. Riesgos y limitaciones que permanecen

### P0 — antes de considerar la versión estable

1. **Ejecutar `mvn clean test` y arrancar Spring.** La compilación focalizada no sustituye la integración completa.
2. **Probar la migración en una copia de MySQL.** Verificar constraints y datos históricos de `route_stops`.
3. **Realizar FIVE_DAY pareado con varias semillas.** Medir servicio neto y tiempo de cómputo.
4. **Manifiesto por pedido.** El vehículo persiste carga total, no una tabla exacta pedido–vehículo–cantidad. La recuperación usa rutas y reglas conservadoras; un manifiesto explícito eliminaría ambigüedades.
5. **Persistencia de telemetría.** Transferencias completadas/canceladas viven en memoria; conviene persistir rescates, fallos, tiempos de espera y motivo de rechazo.

### P1 — mayor beneficio algorítmico esperado

1. **`SERVICE_MAX` verdaderamente integrado en ALNS.** Los no atendidos del seed todavía entran principalmente mediante rescate posterior; deberían formar parte de los movimientos destroy/repair.
2. **Evaluación incremental.** Muchos candidatos reprograman rutas completas. Mantener deltas de tiempo, carga, costo y slack reduciría el costo por iteración.
3. **Candidatos exactos de almacén.** Manhattan ordena alternativas; conviene evaluar varios almacenes con shortest path y bloqueos antes de descartar un pedido.
4. **Índice espacial de vehículos.** Evitar examinar toda la flota para cada riesgo mediante buckets de retícula o nearest-neighbor.
5. **Coalescencia de eventos.** Agrupar eventos que ocurren en una ventana simulada pequeña para no ejecutar replanificaciones consecutivas equivalentes.
6. **Prueba de reposición 23:59:59.** Validar reservas, recargas y transbordos que crucen el reabastecimiento diario.
7. **Paridad SA/ALNS.** Solo necesaria si se volverá a comparar ambos algoritmos.

### P2 — mejoras posteriores

1. intervalo rolling adaptativo: 5–10 min bajo presión y 20–30 min sin riesgo;
2. varios rescates simultáneos mediante grafo de conflictos entre vehículos/pedidos;
3. precarga controlada e inventario móvil;
4. tuning pareado de tamaño destroy, reacción y presupuesto;
5. endpoint de observabilidad del rolling horizon y transbordos.

### No recomendado todavía

- transbordos multi-salto;
- cadenas de tres o más vehículos;
- encuentros en puntos arbitrarios de una arista;
- aumentar operadores ALNS antes de medir las mejoras estructurales.

Estas extensiones amplían fuertemente el espacio de sincronización y hacen más difícil auditar la conservación de carga.

## 6. Experimento mínimo de aceptación

Ejecutar con la misma semilla y presupuesto:

| Variante | Rescate preventivo | Transbordos |
|---|---:|---:|
| A | no | no |
| B | sí | no |
| C | sí | sí |

Criterios mínimos:

- B no debe aumentar vencidos frente a A;
- C debe reducir vencidos frente a B o, si no los reduce, no justificar su espera/distancia adicional;
- nuevos vencimientos provocados por rescates: 0 en la configuración conservadora;
- conservación de carga: diferencia total 0;
- transferencias ejecutadas más de una vez: 0;
- mismo vehículo con dos rutas simultáneas: 0;
- errores de constraints/persistencia: 0.

## 7. Conclusión

La mejora principal no consiste en añadir otra metaheurística. PaqRap necesitaba una capa de control operacional que reevalúe compromisos ya asignados, movilice carga solo cuando existe beneficio y mantenga ALNS para la optimización global.

La implementación ya cubre esa arquitectura. El siguiente paso correcto es validar integración y medir FIVE_DAY, no ampliar todavía la complejidad del algoritmo.
