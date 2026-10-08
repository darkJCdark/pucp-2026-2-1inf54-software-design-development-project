# Modelo Relacional V2 — PaqRap

## 1. Objetivo

La versión 2 del modelo relacional amplía la V1 para soportar la operación del sistema PaqRap durante la planificación, ejecución y replanificación de entregas.

La V2 incorpora persistencia para:

- estado y tipo de los pedidos;
- historial de estados de pedidos;
- estado operativo de la flota;
- ejecuciones de escenarios;
- planes generados;
- rutas y sus paradas;
- resultados de las ejecuciones;
- movimientos de inventario;
- asociación de averías con una ejecución.

El producto operativo utiliza **Simulated Annealing (SA) como único algoritmo de planificación**.

Por este motivo, la base de datos no almacena un selector de algoritmo ni valores como:

- semilla aleatoria;
- temperatura inicial;
- temperatura mínima;
- factor de enfriamiento;
- iteraciones por temperatura;
- parámetros internos de búsqueda.

Estos parámetros pertenecen a la configuración del backend y no constituyen información funcional que el usuario final deba modificar.

---

# 2. Tablas del modelo

La V2 contiene **15 tablas**:

1. `orders`
2. `warehouses`
3. `vehicle_type_parameters`
4. `vehicles`
5. `maintenance_days`
6. `breakdown_events`
7. `road_blocks`
8. `road_block_nodes`
9. `order_status_history`
10. `scenario_executions`
11. `scenario_results`
12. `plans`
13. `routes`
14. `route_stops`
15. `inventory_movements`

Las primeras ocho provienen de la V1 y algunas son extendidas en la V2.

---

# 3. orders

Representa los pedidos registrados en el sistema.

## Campos

| Campo | Tipo | Descripción |
|---|---|---|
| `order_id` | VARCHAR(64) | Identificador único del pedido |
| `client_id` | VARCHAR(32) | Identificador del cliente |
| `destination_x` | SMALLINT UNSIGNED | Coordenada X de destino |
| `destination_y` | SMALLINT UNSIGNED | Coordenada Y de destino |
| `packages` | INT | Cantidad total de paquetes |
| `registered_at` | TIMESTAMP(6) | Momento de registro |
| `deadline` | TIMESTAMP(6) | Fecha/hora límite de entrega |
| `status` | VARCHAR(20) | Estado actual del pedido |
| `delivery_type` | VARCHAR(20) | Tipo de entrega |
| `promised_hours` | SMALLINT UNSIGNED | Plazo contratado en horas |
| `delivered_at` | TIMESTAMP(6) NULL | Momento de entrega final |

## Estados permitidos

```text
REGISTERED
ASSIGNED
IN_TRANSIT
DELIVERED