# PaqRap backend — rescate preventivo, transbordos y rolling horizon

Esta entrega parte de `paqrap-back-gate-cupo-ociosidad(1).zip` e integra cuatro mejoras sobre el backend Java/Spring:

1. rescate preventivo de pedidos ya asignados cuya ETA está en riesgo;
2. transbordos físicos programados entre vehículos;
3. política multi-almacén moderada y reutilización de carga residual;
4. rolling horizon con revisión ligera de ETAs y eventos operativos.

No incluye el frontend. La API de plan ya expone las paradas de transbordo, pero el mapa deberá adaptarse para representarlas explícitamente.

## 1. Rescate preventivo

`RescatePreventivoPedidosService` revisa entregas futuras de las rutas vigentes. Un pedido entra en evaluación cuando su holgura estimada es menor o igual que la ventana configurada:

```text
holgura = deadline - ETA prevista
```

Orden de búsqueda:

```text
pedido en riesgo
    ↓
¿todavía no fue cargado?
    ├─ sí → retirar pickup + entrega de la ruta original e insertarlo en otra ruta
    └─ no → buscar un transbordo físico sincronizado
```

Salvaguardas predeterminadas:

- mejora mínima de ETA: 15 minutos;
- máximo 1 rescate por ciclo;
- máximo 2 reasignaciones por pedido;
- cooldown de 30 minutos;
- espera máxima de encuentro: 15 minutos;
- presupuesto del rescate: 10 % del ciclo, entre 25 y 150 ms;
- no se acepta una alternativa que vuelva tardía una entrega antes factible;
- se conserva el número total de paquetes.

## 2. Transbordos físicos

Se incorporó `TransferStop` con dirección `DONATE` o `RECEIVE`. Cada encuentro válido requiere dos paradas recíprocas con:

- mismo identificador de transferencia;
- mismo pedido y cantidad;
- mismo nodo seguro;
- misma hora de inicio;
- vehículos contraparte coherentes;
- carga suficiente en el donante;
- capacidad suficiente en el receptor.

La carga se mueve únicamente cuando ambos participantes llegan al nodo y comienza el servicio. El ejecutor aplica la transferencia una sola vez.

Si el encuentro falla:

1. se interrumpen ambas rutas;
2. se registra el manifiesto del fallo;
3. se solicita replanificación;
4. la carga permanece en el donante;
5. se intenta reconstruir la entrega desde el donante y preservar la continuación del receptor;
6. si todavía no puede reconstruirse, ambos vehículos y sus compromisos futuros quedan protegidos para evitar duplicación de carga.

## 3. Política de almacenes moderada

Los almacenes intermedios siguen siendo utilizables, pero ya no atraen vehículos solo para distribuir la flota visualmente.

Reglas principales:

- Central gana los empates;
- un intermedio atrae el cierre de ruta solo si ahorra al menos 3 km frente a Central;
- el número preferido de vehículos ociosos por intermedio es 3, aplicado como penalización gradual, no como veto;
- cada vehículo adicional sobre la preferencia añade una penalización heurística de 5 km;
- stock intermedio menor que la reserva táctica de 50 unidades recibe una penalización de 6 km;
- ante urgencia, la ocupación del almacén no impide una alternativa factible;
- no se crean viajes vacíos solo para cumplir cupos;
- un vehículo disponible con carga residual ya no queda excluido: puede entregar directamente o recoger solo la cantidad faltante.

## 4. Rolling horizon

El motor conserva la optimización periódica completa, pero añade una revisión ligera cada 15 minutos simulados:

```text
cada 15 min simulados
    ↓
escanear ETAs comprometidas
    ↓
¿existe una entrega con holgura ≤ 60 min?
    ├─ no → continuar ejecución
    └─ sí → activar rescate local
```

Eventos adicionales:

- fin de ruta: reactiva inmediatamente el vehículo;
- fallo de transbordo: fuerza recuperación global;
- avería o bloqueo: mantiene replanificación inmediata;
- nuevo pedido: mantiene replanificación por evento;
- ciclo periódico: mantiene ALNS/SA global aproximadamente cada hora simulada.

Una revisión local no desplaza la siguiente optimización periódica. Si el rescate de ETA resuelve el riesgo, no se consume automáticamente otra ejecución completa de ALNS.

El horizonte configurable de 36 horas se usa para consultar restricciones futuras relevantes. No se utilizan pedidos registrados en el futuro.

## 5. Correcciones complementarias

- `DetectorColapso` considera pedidos asignados/en tránsito, no solo `REGISTERED`.
- La cota optimista de colapso contempla todos los almacenes y posiciones habilitadas, no solo Central.
- Se evita el patrón N+1 al consultar pedidos comprometidos durante el escaneo de ETAs.
- El ranking urgente vuelve a usar costo como criterio principal una vez filtradas las alternativas que cumplen SLA; ETA queda como desempate.
- El estado de carga durante la espera de un transbordo conserva `loadBefore` hasta que realmente empieza el servicio.
- La persistencia incluye integridad referencial hacia el vehículo contraparte.

## 6. Migración de base de datos

Antes de iniciar esta versión:

1. realizar una copia de seguridad de MySQL;
2. detener el backend;
3. ejecutar una sola vez:

```text
database/paqrap_migration_v2_vehicle_transfers.sql
```

La migración añade a `route_stops`:

- `transfer_packages`;
- `transfer_id`;
- `counterparty_vehicle_id`;
- `transfer_direction`;
- `planned_service_start_at`;
- índices y restricciones de integridad.

No ejecutar el esquema completo sobre una base existente.

## 7. Configuración

PowerShell:

```powershell
$env:PAQRAP_PLAN_ALGORITMO="ALNS"
$env:PAQRAP_PLAN_OBJETIVO="SERVICE_MAX"

$env:PAQRAP_RESCATE_PREVENTIVO="true"
$env:PAQRAP_TRANSBORDOS="true"
$env:PAQRAP_RESCATE_VENTANA_RIESGO="60m"
$env:PAQRAP_RESCATE_MEJORA_MINIMA="15m"
$env:PAQRAP_TRANSBORDO_ESPERA_MAXIMA="15m"
$env:PAQRAP_RESCATE_COOLDOWN="30m"
$env:PAQRAP_RESCATE_MAX_REASIGNACIONES="2"
$env:PAQRAP_RESCATE_MAX_POR_CICLO="1"
$env:PAQRAP_RESCATE_FRACCION_PRESUPUESTO="0.10"
$env:PAQRAP_RESCATE_PRESUPUESTO_MIN="25ms"
$env:PAQRAP_RESCATE_PRESUPUESTO_MAX="150ms"

$env:PAQRAP_ROLLING_INTERVALO_REVISION="15m"
$env:PAQRAP_ROLLING_VENTANA_RIESGO="60m"
$env:PAQRAP_ROLLING_HORIZONTE="36h"
$env:PAQRAP_ROLLING_REPLANIFICAR_FIN_RUTA="true"

mvn spring-boot:run
```

## 8. Validación

Ejecutar desde la raíz del backend:

```powershell
.\VALIDAR_RESCATE_TRANSBORDOS_ROLLING.ps1
```

La validación local debe incluir:

1. pruebas focalizadas;
2. suite Maven completa;
3. arranque del contexto Spring;
4. corrida FIVE_DAY controlada.

En el entorno donde se preparó esta entrega se compiló el código modificado con Java 21 y pasaron 45 pruebas focalizadas únicas. Maven no estaba disponible, por lo que la suite completa de Spring/Surefire y una corrida FIVE_DAY no se ejecutaron aquí.

## 9. Experimento recomendado

Usar mismo mes, semilla, flota, bloqueos, presupuesto y hora inicial.

| Variante | Rescate | Transbordos |
|---|---:|---:|
| A | desactivado | desactivado |
| B | activado | desactivado |
| C | activado | activado |

Registrar al menos en +6 h, +12 h, +24 h y final:

- llegados;
- entregados a tiempo;
- vencidos sin entregar;
- pendientes;
- costo y distancia;
- rescates aceptados;
- transferencias completadas y canceladas;
- espera de encuentros;
- uso y stock por almacén;
- tiempo de cómputo y número de replanificaciones.

La mejora debe juzgarse por vencimientos evitados y servicio neto, no por el número de transbordos.
