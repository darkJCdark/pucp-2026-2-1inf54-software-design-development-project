# Modelo Relacional PaqRap — V2

## 1. Objetivo

La versión V2 extiende el modelo relacional V1 para soportar la integración del backend
con las funcionalidades operativas de PaqRap.

La V1 se conserva como línea base. La V2 añade persistencia para:

- estado y modalidad de los pedidos;
- historial de estados del pedido;
- ejecuciones de los escenarios operativos;
- resultados de las ejecuciones;
- planes y replanificaciones;
- rutas y paradas;
- movimientos de inventario de los almacenes.

La V2 no modifica la lógica interna de los algoritmos GRASP ni Simulated Annealing.
La base de datos persiste entradas, estado operacional y resultados del planificador.

---

## 2. Tablas heredadas de V1

Se mantienen las siguientes ocho tablas:

1. `orders`
2. `warehouses`
3. `vehicle_type_parameters`
4. `vehicles`
5. `maintenance_days`
6. `breakdown_events`
7. `road_blocks`
8. `road_block_nodes`

La tabla `orders` recibe nuevos atributos en V2.

---

## 3. Modificación de `orders`

Se mantienen los campos de V1:

- `order_id`
- `client_id`
- `destination_x`
- `destination_y`
- `packages`
- `registered_at`
- `deadline`

Se agregan:

| Campo | Descripción |
|---|---|
| `status` | Estado operativo actual del pedido |
| `delivery_type` | Modalidad regular o priorizada |
| `promised_hours` | Plazo contratado en horas |
| `delivered_at` | Fecha y hora efectiva de entrega, nullable |

### Estados permitidos

- `REGISTERED`
- `ASSIGNED`
- `IN_TRANSIT`
- `DELIVERED`

### Modalidades

- `REGULAR`
- `PRIORITY`

### Plazos

Para modalidad regular:

`promised_hours = 36`

Para modalidad priorizada:

`promised_hours IN (4, 8, 12, 18)`

El campo `deadline` continúa siendo almacenado porque representa la fecha y hora límite
calculada para el pedido y es utilizada directamente por el planificador.

---

## 4. `order_status_history`

Registra la evolución temporal de un pedido y permite reconstruir su línea de tiempo.

| Campo | Rol |
|---|---|
| `status_history_id` | PK |
| `order_id` | FK → orders |
| `status` | Estado alcanzado |
| `changed_at` | Fecha/hora del cambio |
| `vehicle_id` | FK → vehicles, nullable |
| `warehouse_id` | FK → warehouses, nullable |

Relación:

`orders 1 --- N order_status_history`

Esta tabla permite conservar asignaciones y reasignaciones sin sobrescribir el historial.

---

## 5. `scenario_executions`

Representa una ejecución operacional del sistema.

| Campo | Rol |
|---|---|
| `execution_id` | PK |
| `scenario_type` | Tipo de escenario |
| `algorithm_mode` | Algoritmo o modalidad de comparación |
| `status` | Estado de la ejecución |
| `started_at` | Inicio real de ejecución |
| `finished_at` | Fin real, nullable |
| `simulation_started_at` | Inicio del tiempo simulado |
| `simulation_finished_at` | Fin del tiempo simulado, nullable |
| `collapse_at` | Momento de colapso, nullable |

### Escenarios

- `DAY_TO_DAY`
- `FIVE_DAY`
- `COLLAPSE`

### Modalidad de algoritmo

- `GRASP`
- `SA`
- `BOTH`

### Estados de ejecución

- `CREATED`
- `RUNNING`
- `COMPLETED`
- `COLLAPSED`
- `FAILED`

---

## 6. `scenario_results`

Almacena las métricas finales de una ejecución.

La clave se compone de `execution_id` y `algorithm`, lo que permite almacenar dos
resultados cuando una misma simulación compara GRASP y SA.

| Campo | Rol |
|---|---|
| `execution_id` | PK/FK → scenario_executions |
| `algorithm` | PK: GRASP o SA |
| `total_orders` | Pedidos procesados |
| `delivered_orders` | Pedidos entregados |
| `on_time_orders` | Pedidos dentro del plazo |
| `undelivered_orders` | Pedidos no entregados |
| `total_distance_km` | Distancia total |
| `total_cost` | Costo operacional |
| `computation_time_ms` | Tiempo de cómputo |
| `breakdown_count` | Averías registradas |
| `road_block_count` | Bloqueos registrados |
| `replanning_count` | Número de replanificaciones |
| `created_at` | Momento de registro |

Relación:

`scenario_executions 1 --- N scenario_results`

En una ejecución normal habrá un resultado.
En una comparación GRASP/SA podrá haber dos.

---

## 7. `plans`

Representa un plan generado por el componente planificador.

| Campo | Rol |
|---|---|
| `plan_id` | PK |
| `execution_id` | FK → scenario_executions |
| `algorithm` | GRASP o SA |
| `plan_type` | INITIAL o REPLANNING |
| `created_at` | Momento de generación |
| `feasible` | Indica factibilidad |
| `total_distance_km` | Distancia total |
| `total_cost` | Costo total |

Relación:

`scenario_executions 1 --- N plans`

No se sobrescribe el plan anterior cuando ocurre una replanificación.

---

## 8. `routes`

Representa cada ruta perteneciente a un plan.

| Campo | Rol |
|---|---|
| `route_id` | PK |
| `plan_id` | FK → plans |
| `vehicle_id` | FK → vehicles |
| `start_warehouse_id` | FK → warehouses, nullable |
| `origin_x` | Coordenada inicial X |
| `origin_y` | Coordenada inicial Y |
| `departure_at` | Hora de salida |
| `total_distance_km` | Distancia de la ruta |
| `total_cost` | Costo de la ruta |

Relaciones:

`plans 1 --- N routes`

`vehicles 1 --- N routes`

`warehouses 1 --- N routes` (cuando la ruta se inicia en un almacén)

Las coordenadas de origen se conservan porque una replanificación puede comenzar desde
la posición actual del vehículo y no necesariamente desde un almacén.

---

## 9. `route_stops`

Representa las paradas ordenadas de una ruta.

| Campo | Rol |
|---|---|
| `route_stop_id` | PK |
| `route_id` | FK → routes |
| `sequence_no` | Posición dentro de la ruta |
| `stop_type` | DELIVERY o WAREHOUSE |
| `order_id` | FK → orders, nullable |
| `warehouse_id` | FK → warehouses, nullable |
| `delivered_packages` | Cantidad entregada, nullable |
| `x` | Coordenada X |
| `y` | Coordenada Y |
| `planned_arrival_at` | Hora planificada de llegada |

Se define una restricción UNIQUE sobre:

`(route_id, sequence_no)`

Relación:

`routes 1 --- N route_stops`

Un mismo pedido puede aparecer en más de un `route_stop`, permitiendo representar
entregas parciales.

La suma de `delivered_packages` asociada a un pedido deberá corresponder a la demanda
atendida por el plan.

---

## 10. `inventory_movements`

Representa el kardex de los almacenes.

| Campo | Rol |
|---|---|
| `movement_id` | PK |
| `warehouse_id` | FK → warehouses |
| `execution_id` | FK → scenario_executions, nullable |
| `order_id` | FK → orders, nullable |
| `movement_type` | Tipo de movimiento |
| `quantity` | Cantidad |
| `occurred_at` | Fecha/hora del movimiento |

Tipos inicialmente considerados:

- `DISPATCH`
- `RECHARGE`

Relaciones:

`warehouses 1 --- N inventory_movements`

`scenario_executions 1 --- N inventory_movements`

`orders 1 --- N inventory_movements`

La tabla registra movimientos; no reemplaza el valor de capacidad del almacén.

---

## 11. Resumen del modelo V2

La versión V2 contiene 15 tablas.

### V1

1. orders
2. warehouses
3. vehicle_type_parameters
4. vehicles
5. maintenance_days
6. breakdown_events
7. road_blocks
8. road_block_nodes

### Nuevas en V2

9. order_status_history
10. scenario_executions
11. scenario_results
12. plans
13. routes
14. route_stops
15. inventory_movements

---

## 12. Relaciones principales

vehicle_type_parameters 1 --- N vehicles

vehicles 1 --- N maintenance_days

vehicles 1 --- N breakdown_events

road_blocks 1 --- N road_block_nodes

orders 1 --- N order_status_history

scenario_executions 1 --- N scenario_results

scenario_executions 1 --- N plans

plans 1 --- N routes

vehicles 1 --- N routes

routes 1 --- N route_stops

orders 1 --- N route_stops

warehouses 1 --- N route_stops

warehouses 1 --- N inventory_movements

scenario_executions 1 --- N inventory_movements

orders 1 --- N inventory_movements

---

## 13. Alcance de la V2

La V2 permite persistir la información necesaria para que el backend avance con:

- CU relacionados con registro y seguimiento de pedidos;
- modalidad regular y priorizada;
- ejecución de escenarios;
- almacenamiento de resultados;
- planificación y replanificación;
- rutas;
- entregas parciales;
- inventario y recargas.

La V2 no pretende constituir el modelo final del proyecto.

Quedan para futuras iteraciones, si los casos de uso finalmente lo requieren:

- autenticación y usuarios;
- configuración editable de parámetros;
- almacenamiento detallado de métricas experimentales;
- reportes históricos especializados;
- auditoría general del sistema;
- persistencia de información que pueda derivarse sin necesidad de almacenarse.