# Modelo Relacional V1 — PaqRap

## 1. Propósito

Este documento define el modelo relacional inicial de PaqRap.

La versión V1 busca proporcionar una base de persistencia mínima y evolutiva
para desbloquear el desarrollo del backend. No pretende representar todavía
todas las estructuras de dominio ni todos los objetos utilizados durante la
planificación.

El modelo se basa en las entidades persistentes identificadas en el backend
actual y en las reglas funcionales confirmadas para el proyecto.

---

## 2. Tablas de la versión V1

La versión inicial contiene ocho tablas:

1. `orders`
2. `warehouses`
3. `vehicle_type_parameters`
4. `vehicles`
5. `maintenance_days`
6. `breakdown_events`
7. `road_blocks`
8. `road_block_nodes`

---

## 3. Tabla `orders`

Representa los pedidos registrados en el sistema.

### Columnas

| Columna | Tipo MySQL | Restricciones |
|---|---|---|
| `order_id` | `VARCHAR(64)` | PK, NOT NULL |
| `client_id` | `VARCHAR(32)` | NOT NULL |
| `destination_x` | `SMALLINT UNSIGNED` | NOT NULL, entre 0 y 70 |
| `destination_y` | `SMALLINT UNSIGNED` | NOT NULL, entre 0 y 50 |
| `packages` | `INT UNSIGNED` | NOT NULL, mayor que 0 |
| `registered_at` | `TIMESTAMP(6)` | NOT NULL |
| `deadline` | `TIMESTAMP(6)` | NOT NULL, posterior a `registered_at` |

### Claves e índices

- PK: `order_id`
- Índice sobre `client_id`
- Índice sobre `registered_at`
- Índice sobre `deadline`

### Observaciones

`client_id` se almacena porque forma parte de los datos de entrada de los
pedidos. En esta versión no se crea una tabla `clients`, ya que actualmente no
existe información adicional del cliente que justifique una entidad propia.

Las entregas parciales son soportadas por el dominio, pero no se almacenan
todavía porque esta versión no persiste planes ni rutas.

---

## 4. Tabla `warehouses`

Representa el almacén central y los almacenes intermedios.

### Columnas

| Columna | Tipo MySQL | Restricciones |
|---|---|---|
| `warehouse_id` | `VARCHAR(32)` | PK, NOT NULL |
| `warehouse_kind` | `VARCHAR(16)` | NOT NULL |
| `x` | `SMALLINT UNSIGNED` | NOT NULL, entre 0 y 70 |
| `y` | `SMALLINT UNSIGNED` | NOT NULL, entre 0 y 50 |
| `initial_stock` | `INT UNSIGNED` | NULL para central |

### Claves y restricciones

- PK: `warehouse_id`
- UNIQUE: `(x, y)`
- `warehouse_kind` admite:
  - `CENTRAL`
  - `INTERMEDIATE`

### Datos iniciales

| warehouse_id | Tipo | X | Y | Stock |
|---|---|---:|---:|---:|
| `CENTRAL` | CENTRAL | 27 | 14 | NULL |
| `NORTHWEST` | INTERMEDIATE | 12 | 38 | 1000 |
| `EAST` | INTERMEDIATE | 57 | 27 | 1000 |

El almacén central utiliza `NULL` en `initial_stock` para representar inventario
no finito. Los almacenes intermedios utilizan stock finito.

---

## 5. Tabla `vehicle_type_parameters`

Contiene los parámetros operativos asociados a cada tipo de vehículo.

Se persisten porque determinados parámetros, especialmente la velocidad,
pueden modificarse por tipo de unidad durante la ejecución y aplicarse a una
iteración posterior de planificación.

### Columnas

| Columna | Tipo MySQL | Restricciones |
|---|---|---|
| `vehicle_type` | `VARCHAR(20)` | PK, NOT NULL |
| `capacity_packages` | `SMALLINT UNSIGNED` | NOT NULL, mayor que 0 |
| `speed_kmh` | `DECIMAL(6,2)` | NOT NULL, mayor que 0 |
| `cost_per_km` | `DECIMAL(8,2)` | NOT NULL, mayor o igual que 0 |
| `updated_at` | `TIMESTAMP(6)` | NOT NULL, actualización automática |

### Valores iniciales

| Tipo | Capacidad | Velocidad km/h | Costo por km |
|---|---:|---:|---:|
| `CAR` | 24 | 40 | 8.00 |
| `MOTORCYCLE` | 8 | 25 | 6.00 |
| `BICYCLE` | 4 | 12 | 3.00 |

---

## 6. Tabla `vehicles`

Representa las unidades físicas de transporte disponibles en PaqRap.

### Columnas

| Columna | Tipo MySQL | Restricciones |
|---|---|---|
| `vehicle_id` | `VARCHAR(8)` | PK, NOT NULL |
| `vehicle_type` | `VARCHAR(20)` | FK, NOT NULL |
| `available` | `BOOLEAN` | NOT NULL, DEFAULT TRUE |

### Relaciones

`vehicle_type_parameters` 1:N `vehicles`

### Foreign Key

`vehicles.vehicle_type`
→ `vehicle_type_parameters.vehicle_type`

Política:

- ON UPDATE CASCADE
- ON DELETE RESTRICT

### Flota inicial

- 10 autos
- 15 motocicletas
- 12 bicicletas

Total inicial: 37 vehículos.

Los identificadores siguen el esquema `TTNN`, por ejemplo:

- `TA01`
- `TM03`
- `TB10`

---

## 7. Tabla `maintenance_days`

Representa la programación de mantenimiento preventivo de los vehículos.

### Columnas

| Columna | Tipo MySQL | Restricciones |
|---|---|---|
| `vehicle_id` | `VARCHAR(8)` | PK parcial, FK, NOT NULL |
| `maintenance_date` | `DATE` | PK parcial, NOT NULL |

### Clave primaria

PK compuesta:

`(vehicle_id, maintenance_date)`

### Foreign Key

`vehicle_id`
→ `vehicles.vehicle_id`

### Índices

Índice sobre:

`maintenance_date`

### Observaciones

Una unidad registrada en mantenimiento no se considera disponible para
planificación durante el día correspondiente.

La duración diferenciada del mantenimiento según tipo de vehículo no se
incorpora todavía como restricción de base de datos porque dicha regla aún no
se encuentra suficientemente cerrada para esta versión.

---

## 8. Tabla `breakdown_events`

Representa las averías registradas para las unidades de transporte.

### Columnas

| Columna | Tipo MySQL | Restricciones |
|---|---|---|
| `breakdown_id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT |
| `vehicle_id` | `VARCHAR(8)` | FK, NOT NULL |
| `breakdown_type` | `VARCHAR(20)` | NOT NULL |
| `occurred_at` | `TIMESTAMP(6)` | NOT NULL |
| `location_x` | `SMALLINT UNSIGNED` | NOT NULL, entre 0 y 70 |
| `location_y` | `SMALLINT UNSIGNED` | NOT NULL, entre 0 y 50 |

### Tipos permitidos

- `MINOR`
- `INTERMEDIATE`
- `MAJOR`

### Relación

`vehicles` 1:N `breakdown_events`

### Índices

- `(vehicle_id, occurred_at)`
- `occurred_at`

La resolución temporal de la avería se calcula en la lógica del backend y no
se almacena de forma redundante en esta versión.

---

## 9. Tabla `road_blocks`

Representa los bloqueos viales planificados.

### Columnas

| Columna | Tipo MySQL | Restricciones |
|---|---|---|
| `road_block_id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT |
| `starts_at` | `TIMESTAMP(6)` | NOT NULL |
| `ends_at` | `TIMESTAMP(6)` | NOT NULL |

### Restricción

`ends_at > starts_at`

### Índices

Índice compuesto:

`(starts_at, ends_at)`

La geometría del bloqueo se almacena de forma separada mediante
`road_block_nodes`.

---

## 10. Tabla `road_block_nodes`

Representa los puntos ordenados que componen la polilínea de un bloqueo.

### Columnas

| Columna | Tipo MySQL | Restricciones |
|---|---|---|
| `road_block_id` | `BIGINT UNSIGNED` | PK parcial, FK |
| `sequence_no` | `SMALLINT UNSIGNED` | PK parcial |
| `x` | `SMALLINT UNSIGNED` | NOT NULL, entre 0 y 70 |
| `y` | `SMALLINT UNSIGNED` | NOT NULL, entre 0 y 50 |

### Clave primaria

PK compuesta:

`(road_block_id, sequence_no)`

### Foreign Key

`road_block_id`
→ `road_blocks.road_block_id`

Política:

`ON DELETE CASCADE`

### Ejemplo

Una polilínea:

`(31,21) → (32,21) → (33,21) → (34,21)`

se almacena como:

| road_block_id | sequence_no | x | y |
|---:|---:|---:|---:|
| 15 | 1 | 31 | 21 |
| 15 | 2 | 32 | 21 |
| 15 | 3 | 33 | 21 |
| 15 | 4 | 34 | 21 |

Los segmentos entre nodos consecutivos se reconstruyen en memoria.

---

## 11. Relaciones del modelo V1

Relaciones persistentes:

vehicle_type_parameters
1 ───── N
vehicles

vehicles
1 ───── N
maintenance_days

vehicles
1 ───── N
breakdown_events

road_blocks
1 ───── N
road_block_nodes

En esta versión `orders` y `warehouses` no poseen relaciones persistentes con
otras tablas debido a que las rutas y entregas todavía no se almacenan.

---

## 12. Elementos que no se persisten en V1

No se crean tablas para:

- `Client`
- `Location`
- `StreetSegment`
- `RoadNetwork`
- `RoadLeg`
- `RoadPath`
- `TraversalOutcome`
- `InventorySnapshot`
- `VehicleOperationalState`
- `OperationalSnapshot`
- `OperationalPlan`
- `DeliveryRoute`
- `RouteStop`
- `DeliveryStop`
- `WarehouseVisit`
- `ScheduledDeliveryRoute`
- `ScheduledRouteStop`
- `PlanEvaluation`
- `PlanViolation`
- `ResultadoPlanificacion`
- GRASP
- Simulated Annealing
- `InitialPlanBuilder`
- escenarios

Estas estructuras son actualmente objetos de dominio, objetos temporales,
resultados derivados o componentes algorítmicos.

---

## 13. Decisiones postergadas para futuras versiones

La versión V1 deja preparadas para evolución las siguientes decisiones:

- Persistencia del estado operacional actual de los vehículos.
- Historial de planificación.
- Persistencia de rutas aprobadas o ejecutadas.
- Persistencia de entregas parciales realizadas.
- Kardex o movimientos de inventario.
- Historial/versionado de parámetros de vehículos.
- Persistencia formal de escenarios.
- Auditoría de evaluaciones y violaciones de planificación.

Estas funcionalidades deberán incorporarse mediante nuevas versiones o
migraciones cuando los requisitos funcionales correspondientes se encuentren
definidos.

---

## 14. Principio de evolución

El modelo V1 prioriza una persistencia mínima necesaria para iniciar la
integración con el backend.

Las futuras funcionalidades deberán extender el esquema mediante migraciones,
evitando modificar de forma destructiva los datos persistidos por versiones
anteriores.