# PaqRap — 1INF54 Grupo 4D

Sistema de planificación de rutas de reparto urbano. El equipo compara dos
metaheurísticos —GRASP y Simulated Annealing (SA)— sobre el mismo
problema, para elegir uno como algoritmo final (el IEN / diseño de
experimentos corre ambos sobre el mismo escenario y compara resultados).

## Estructura (rama `algoritmo-1-GRASP-entregable-semana-4`)

```
/
├── pom.xml            <- agregador Maven (packaging=pom)
├── dominio/            <- modelo de dominio UNICO (pe.edu.pucp.paqrap.planner)
│   └── src/main/java/pe/edu/pucp/paqrap/planner/{domain,route}/...
└── grasp/              <- algoritmo GRASP, depende de dominio via Maven
    └── src/main/java/pe/pucp/paqrap/{planificador,modelo,reportes,app}/...
```

El algoritmo SA vive en su propia rama (`algoritmo-2-SA-entregable-semana-4`)
y **todavía no depende de `dominio`** — ver "Unificación de dominio" abajo.

## Comandos

```bash
# Compilar + instalar ambos modulos (una vez, o tras cualquier cambio en dominio/)
mvn install -DskipTests

# Correr las pruebas
mvn test

# Correr el ejemplo de uso
cd grasp && mvn compile exec:java
```

Requiere JDK 21 (el dominio usa records y sealed interfaces).

## Unificación de dominio (14-sep-2026)

Hasta esta rama, GRASP y SA tenían modelos de dominio independientes
(`Pedido`/`Order`, `Almacen`/`Warehouse`, etc., en dos repositorios
separados). Esta rama extrae el módulo `dominio/` con el modelo real de
SA (`pe.edu.pucp.paqrap.planner`, copiado desde `paqrap-SAalgoritmos` /
rama `algoritmo-2-SA-entregable-semana-4`) como **fuente única**, y migra
`GraspPlanificador` para depender de él vía Maven — no hay archivos
duplicados de `domain`/`route` en ningún otro paquete.

**Pendiente para que la unificación sea completa:** que la rama de SA
también dependa de `paqrap-dominio` en vez de mantener su propia copia de
`domain`/`route`. Esa parte no se hizo aquí porque toca directamente el
código de Cristhian/Jorge — es una decisión y un cambio que les
corresponde a ellos, coordinados con el equipo. Esta rama deja el módulo
listo para que lo adopten.

Ver también el historial de commits de esta rama: cada corrección va en su
propio commit, con la razón documentada en el mensaje.

## Correcciones portadas a `dominio/` (afectan a ambos algoritmos)

- **Refrigerio (hora de alimentación) ausente en SA.** Se buscó
  `refriger`/`meal`/`break` en todo `paqrap-SAalgoritmos` y no hubo
  ninguna coincidencia, pese a que el enunciado lo exige y una versión
  previa de `GraspPlanificador` ya lo tenía. Ahora vive en
  `RouteScheduler` (una vez por turno, con ≥1h de margen respecto a
  cualquier cambio de turno — `ShiftSchedule.mealWindow`), heredado
  automáticamente por cualquier algoritmo que use este módulo.
- **Límite de 80 km por tramo** (hoja "Flota": *"Máxima distancia ida: 80
  km"*). Interpretación adoptada: ningún tramo individual entre dos
  paradas consecutivas puede superar 80 km — no la distancia acumulada
  desde la última salida de almacén. Es una lectura razonable pero no la
  única posible; confirmar con el docente si hace falta. Implementado como
  `PlanViolationType.LEG_DISTANCE_EXCEEDED` en `OperationalPlanEvaluator`.
- **Semántica de `noAtendidos`, verificada consistente.** La decisión
  confirmada por el docente (un pedido sin vehículo factible queda
  pendiente para la próxima corrida, sin invalidar el resto del plan) NO
  requiere ningún cambio en `OperationalPlanEvaluator`: el conjunto
  `requiredOrders` que recibe `evaluate()` ya lo decide quien llama, no el
  evaluador. `GraspPlanificador` ya lo hace bien (excluye `noAtendidos` de
  `requiredOrders`). Si el algoritmo de SA (paquete `sa/`, no incluido en
  este módulo) pasa siempre *todos* los pedidos como requeridos sin
  filtrar los no atendibles, eso hay que corregirlo en su propio código,
  no en el dominio compartido — señalarlo a Cristhian/Jorge.

## Correcciones portadas a `grasp/`

- **`GraspPlanificador` migrado a `paqrap-dominio`.** Reescrito contra
  `Order`, `Warehouse`, `Vehicle`, `DeliveryRoute`, `RoadNetwork`,
  `OperationalPlanEvaluator`, etc. — sin modelo propio.
- **Costeo marginal cacheado en la fase constructiva**, en vez de
  recalcular la ruta completa vía `RouteScheduler` por cada candidato:
  GRASP evalúa muchos candidatos por pedido (vehículo × posición) para
  construir el RCL, a diferencia de SA que evalúa un solo vecino por
  iteración — copiar el "recálculo completo" de SA penalizaba
  desproporcionadamente a GRASP (~22s para 50 iteraciones con solo 3
  pedidos, medido antes de este cambio; ~5.4x más rápido después). Ver el
  Javadoc de la clase para el detalle de diseño.
- **`ROUTE_NOT_RETURNED_TO_WAREHOUSE` corregido**: la reubicación entre
  rutas permitía insertar una entrega *después* del `WarehouseVisit` de
  regreso al almacén, que siempre debe quedar al final.
- **`NEGATIVE_LOAD` corregido**: mover o intercambiar una entrega entre
  rutas no ajustaba la cantidad recogida en el `WarehouseVisit` que abre
  ese tramo — la ruta terminaba "entregando" más de lo que había
  recogido.
- **Rutas vacías tras una reubicación, corregido.** Si una reubicación deja
  la ruta de origen sin ninguna entrega, ahora se elimina del plan
  (`OperationalPlan.withoutRoute`) en vez de quedar como un "viaje" con
  cero paradas, que inflaba artificialmente el conteo de viajes de
  `ValidadorUtilizacionFlota` sin aportar carga real.
- **`noAtendidos` reparado en `busquedaLocal`.** Antes, un pedido quedaba
  marcado como no atendido apenas la fase constructiva no encontraba
  candidato en ESE momento del barrido greedy, y nunca se reintentaba --
  ni siquiera dentro de la misma iteración, aunque la búsqueda local
  reordenara las rutas después. Ahora `pasoInsercionPendientes` intenta,
  tras cada pase de 2-opt/reubicación/intercambio, insertar cada pendiente
  en un tramo ya abierto de alguna ruta (nunca abre un `WarehouseVisit`
  nuevo). Solo si sigue sin caber después de esto se considera un caso
  real de colapso, no un artefacto del orden de procesamiento. Prioridad
  ya existente en `esMejorQue` (factibilidad > menos noAtendidos > costo)
  se mantiene intacta -- este cambio hace que se cumpla más seguido.
- **Señal explícita de colapso.** `ResultadoPlanificacion.esColapso()`
  (`!noAtendidos.isEmpty()` sobre el resultado FINAL que devuelve
  `planificar()`) reemplaza el hábito de restar "49 de 50" a mano en el
  log -- el reporte ahora dice directamente `COLAPSO: SI/no`.
- **Coordenadas de almacén actualizadas**: Central (27, 14), Este (57,
  27) — la fila más reciente de la hoja de preguntas y respuestas
  reemplaza a la anterior, (25, 15) y (55, 27). Nor-Oeste (12, 38) no
  cambió.
- **`ValidadorUtilizacionFlota` (nuevo, paquete `reportes`).** Compara el
  plan resultante (de GRASP o de SA — funciona igual para ambos, ya que
  ambos producen un `OperationalPlan` del mismo dominio) contra `NroViajes
  mín` y `Carga Total Aprox. Mínima` de la hoja "Flota", **como reporte
  posterior a la corrida, no como restricción dura dentro de la
  construcción**. Se eligió así porque forzar un número mínimo de viajes
  sin relación a la demanda real obligaría al algoritmo a generar viajes
  vacíos o redundantes solo para "cumplir cuota". Si la intención es que
  sea una restricción dura real, es un cambio distinto — avisar antes.
- **`NEGATIVE_LOAD` real, corregido** (distinto del `NEGATIVE_LOAD` de
  reubicación/intercambio ya corregido arriba). Encontrado probando con
  data real (ver "Data real" abajo): `generarCandidatos` apilaba un 2do
  pedido en el tramo inicial de una ruta sin aumentar la carga recogida —
  ese tramo esta gobernado por `initialLoad`, fijado una vez al crear la
  ruta e inmutable despues. La alternativa correcta (recargar en un
  almacen antes de tomar mas pedidos) existia pero nunca se activaba por
  una condicion que usaba una variable que jamas volvia a 0 -- codigo
  muerto. Con 6 pedidos sinteticos y 37 vehiculos casi nunca hacia falta
  apilar 2 pedidos en un mismo vehiculo, por eso las pruebas nunca lo
  agarraron.
- **`SLA_MISSED` real, corregido.** `esFactibleCandidato` (el chequeo de
  deadline durante la construcción) no modelaba el refrigerio de 1h que sí
  aplica `RouteScheduler` (el evaluador autoritativo) — aceptaba
  candidatos que, segun su propio calculo, llegaban a tiempo, pero que el
  evaluador final rechazaba por llegar 1h tarde. Mismo calculo de
  refrigerio agregado a ambos lugares.

## Data real y escenario real

`data/` (raíz del repo) tiene la data real entregada por el docente:
bloqueos (`data/bloqueos/bloqueo<aammm>.txt`), pedidos
(`data/ventas/ventas<aaaamm>.txt`) y mantenimiento preventivo
(`data/mant.preventivo.09.10.txt`) — formato exacto en la hoja "Preguntas y
Respuestas" del curso, preguntas 7, 8 y 19.

`grasp/src/main/java/pe/pucp/paqrap/modelo/{CargadorPedidos,CargadorBloqueos,
CargadorMantenimiento}.java` parsean esos 3 formatos. Los dos primeros
aceptan una ventana de tiempo opcional para acotar el mes completo (miles de
pedidos) a un escenario concreto — necesario en la práctica, ver
rendimiento abajo. `CargadorMantenimiento` usa la única regla de duración
confirmada por el docente (1 día completo, para cualquier tipo de
vehículo) — la nota que menciona duraciones distintas por tipo
(bicicleta=1 turno, moto=1 día, auto=2 días) está marcada por el propio
docente como pendiente ("FALTA..."), así que no se adivinó ese valor.

`grasp/src/main/java/pe/pucp/paqrap/app/EscenarioReal.java` corre
`GraspPlanificador` de punta a punta contra esta data (no los 3 pedidos de
juguete de `DemoPlanificacion`). Ventana y `maxIteraciones` configurables
por argumentos:

```bash
cd grasp
mvn compile exec:java -Dexec.mainClass=pe.pucp.paqrap.app.EscenarioReal -Dexec.args="7 8 20"
# horaInicio horaFin maxIteraciones, 09-sep-2026 -- todos opcionales
```

**Rendimiento con data real, sin resolver todavía:** el turno completo
(07:00-15:00, 52 pedidos reales) no terminó ni en 5 minutos con
`maxIteraciones=50` — se detuvo la corrida. La generación de candidatos
escala con el largo de ruta ya construido, así que el costo crece más
rápido que lineal con la cantidad de pedidos; con 3-6 pedidos sintéticos
nunca se notó. Recomendación: subir la ventana de a poco (1h → 2h → ...)
para encontrar en qué punto se vuelve impráctico, antes de asumir que un
turno completo corre en un tiempo razonable — esto quedó pendiente de
terminar de explorar.

## Pendiente (sin resolver a propósito, ver el código)

- **Velocidad real por tipo de vehículo**: conflicto sin resolver entre la
  situación auténtica (auto=40, moto=25, bicicleta=12 km/h) y la hoja
  "Flota" (auto=20, moto=40, bicicleta=14 km/h). El sistema sigue siendo
  agnóstico — sin valor por defecto, inyectado por configuración
  (`CargadorParametros`, `grasp/src/main/resources/*.properties`).
- Si el mantenimiento programado por unidad (`MaintenanceCalendar`) aplica
  a los 3 escenarios o solo al de colapso logístico.
- Si `NroViajes mín`/`Carga Total Aprox. Mínima` deben ser restricción
  dura o solo el reporte informativo ya descrito (`ValidadorUtilizacionFlota`).

## Siguiente paso

- Coordinar con Cristhian/Jorge para que la rama de SA dependa de
  `paqrap-dominio` (ver "Unificación de dominio" arriba) — sin eso, la
  unificación real sigue pendiente aunque el módulo ya exista.
- Arnés de experimentación (IEN): correr ambos algoritmos sobre
  exactamente el mismo `OperationalSnapshot` + pedidos + bloqueos, con
  distintas semillas, y comparar resultados. Queda pendiente de diseñar
  una interfaz común (`Planificador` + adapters para GRASP y SA) una vez
  que ambos dependan del mismo módulo de dominio.
