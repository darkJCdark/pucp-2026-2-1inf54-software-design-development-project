Sí. Te dejo un **contexto maestro de PaqRap**, redactado para que puedas copiarlo completo en otro chat y ese chat entienda rápidamente el proyecto, las reglas del caso, el estado actual, los algoritmos y qué necesitas hacer.

---

# CONTEXTO MAESTRO DEL PROYECTO PAQRAP

## 1. Proyecto

El proyecto se llama **PaqRap** y corresponde al curso **Proyecto de Diseño y Desarrollo de Software (1INF54), Ingeniería Informática PUCP**.

PaqRap es un sistema para **planificar, replanificar, simular y monitorear la distribución de un único producto P** mediante una flota heterogénea de:

- autos,
- motos,
- bicicletas.

El sistema debe decidir cómo atender pedidos utilizando vehículos y almacenes, respetando capacidades, inventarios, plazos, bloqueos de calles, averías, mantenimiento y otras restricciones operativas.

El componente principal de optimización utilizará dos metaheurísticas:

- **GRASP**
- **Simulated Annealing (SA)**

Ambos algoritmos deben poder trabajar sobre **el mismo dominio operativo y las mismas reglas de validación** para que sus resultados sean comparables.

---

# 2. Objetivo funcional general

PaqRap debe permitir principalmente:

1. registrar/cargar pedidos;
2. planificar rutas;
3. replanificar frente a cambios o incidencias;
4. administrar flota e inventario;
5. ejecutar distintos escenarios de operación;
6. visualizar vehículos, rutas, pedidos, almacenes y bloqueos;
7. comparar GRASP y Simulated Annealing;
8. detectar el escenario de colapso.

El cumplimiento de los **plazos de entrega es una restricción dura**.

El costo es un criterio de optimización secundario.

No se debe considerar como buena una solución de menor costo si deja pedidos sin atender o incumple plazos.

---

# 3. Producto y pedidos

Solo existe **un producto P** y una única presentación.

Cada pedido contiene, como mínimo:

```text
id
fecha/hora de llegada
cliente
coordenadas X,Y
cantidad
horas límite
```

Formato de pedidos entregado por el profesor:

```text
##d##h##m:posX,posY,cIdCliente,qq,hl
```

Ejemplo:

```text
11d13h31m:45,43,c9167,12,36
```

significa:

```text
día/hora llegada = 11 13:31
destino = (45,43)
cliente = c9167
cantidad = 12
plazo = 36 horas
```

El archivo es mensual.

### Tipos de plazo

Entrega regular:

```text
36 horas
```

Entregas priorizadas:

```text
4 h
8 h
12 h
18 h
```

El plazo comienza desde la fecha/hora en que llega el pedido.

---

# 4. Entregas parciales

**Sí están permitidas las entregas parciales.**

Ejemplo:

```text
Pedido = 5 unidades

Unidad A entrega 2
Unidad B entrega 3
```

Eso constituye dos entregas.

Cada entrega efectiva consume:

```text
1 hora de acondicionamiento
```

independientemente de cuántas unidades se entreguen.

Por ejemplo:

```text
2 unidades → 1 hora
3 unidades → 1 hora
10 unidades → 1 hora
```

El acondicionamiento **NO forma parte del plazo comprometido de entrega**.

En el modelo se diferencia:

```text
Pedido
→ solicitud original

CargaPedido
→ cantidad asignada a una unidad

Entrega
→ cantidad efectivamente entregada
```

---

# 5. Red vial

El mapa es completamente simulado.

No representa Lima real.

Es una retícula rectangular:

```text
70 km × 50 km
```

con:

```text
X = 0..70
Y = 0..50
```

Origen:

```text
(0,0)
```

en la esquina inferior izquierda.

Cada nodo está separado del siguiente por:

```text
1 km
```

Todas las calles:

- son bidireccionales;
- no tienen diagonales;
- no tienen curvas.

Los clientes están ubicados en nodos `(x,y)`.

---

# 6. Almacenes — coordenadas ACTUALIZADAS

Hubo un cambio posterior del profesor.

Las coordenadas vigentes son:

```text
Central:      (27,14)
Nor-Oeste:    (12,38)
Este:         (57,27)
```

Estas reemplazan las coordenadas antiguas `(25,15)` y `(55,27)`.

### Almacén central

Se considera:

```text
inventario permanente / efectivamente infinito
```

### Almacenes intermedios

Cada uno tiene:

```text
capacidad máxima = 1000 productos
```

y se reabastece diariamente a:

```text
23:59:59
```

hasta recuperar su capacidad máxima.

Una unidad puede abastecerse en cualquier almacén que tenga inventario suficiente.

Todas las unidades parten inicialmente del **almacén central**.

---

# 7. Semáforo de almacenes

Se determina mediante porcentajes configurables de inventario despachado.

Referencia utilizada:

```text
0%–33%      → VERDE
33%–66%     → ÁMBAR
>66%        → ROJO
```

Los umbrales deben ser configurables.

---

# 8. Flota actual del caso

La flota entregada por el profesor es:

```text
Autos:       10
Motos:       15
Bicicletas:  12
```

Capacidades:

```text
Auto:       24 productos
Moto:        8 productos
Bicicleta:   4 productos
```

Identificación:

```text
TA01 → Auto 01
TM03 → Moto 03
TB10 → Bicicleta 10
```

Formato:

```text
TTNN
```

donde TT indica el tipo y NN es correlativo.

---

# 9. Velocidades y costos

Velocidades base:

```text
Auto:       40 km/h
Moto:       25 km/h
Bicicleta:  12 km/h
```

Costos:

```text
Auto:       S/ 8/km
Moto:       S/ 6/km
Bicicleta:  S/ 3/km
```

La velocidad se puede cambiar **en caliente**, pero:

- el cambio es por **tipo de unidad**, no por vehículo individual;
- se aplica desde la **siguiente iteración de planificación/replanificación**;
- todos los vehículos pueden ser replanteados en la siguiente iteración, incluso si ya estaban en ruta.

La cantidad de vehículos solo puede modificarse **antes de comenzar una ejecución**. Una vez iniciada, la flota queda fija.

---

# 10. Turnos

Existen tres turnos:

```text
07:00 – 15:00
15:00 – 23:00
23:00 – 07:00
```

Cuando cambia el turno:

> el nuevo conductor alcanza al vehículo donde este se encuentre.

El tiempo de cambio de conductor es despreciable.

Existe además una hora de alimentación/refrigerio por turno, con restricciones temporales respecto a los cambios de turno.

---

# 11. Bloqueos

Los bloqueos son **planificados**, no fortuitos.

Se entregan mediante archivo.

Una avería de un vehículo **no bloquea una calle**.

Los bloqueos están definidos mediante una secuencia de nodos.

Si un vehículo llega a un nodo bloqueado:

- no puede atravesarlo;
- no puede girar hacia lados;
- debe regresar por el mismo tramo mediante giro en U.

En este curso solo se trabajará con **polígonos abiertos**.

### ACTUALIZACIÓN IMPORTANTE PARA EXPERIMENTACIÓN NUMÉRICA

Para el diseño de experimentos:

> **sí deben considerarse los bloqueos.**

El profesor entregó archivos con la información de bloqueos específicamente para ello.

---

# 12. Mantenimiento preventivo

Existe un plan de mantenimiento mediante archivos bimensuales.

Formato:

```text
aaaammdd:TTNN
```

Una unidad programada para mantenimiento:

```text
no está disponible de 00:00 a 23:59
```

Si se encuentra en ruta al comenzar su mantenimiento, debería regresar al Central; idealmente la planificación debe evitar llegar a esa situación.

### ACTUALIZACIÓN IMPORTANTE

Para la **experimentación numérica GRASP vs SA**:

> **NO se debe considerar el mantenimiento preventivo.**

Por tanto:

```text
experimento numérico:
bloqueos ✅
mantenimiento preventivo ❌
```

Esto fue aclarado posteriormente por el profesor.

---

# 13. Averías

Una avería significa que una unidad queda indisponible temporalmente.

## Tipo 1 — menor

```text
indisponible 2 horas
```

## Tipo 2 — intermedia

Regla indicada:

```text
indisponible hasta el final del siguiente turno
```

con permanencia en el lugar por un máximo de 4 horas.

## Tipo 3 — mayor

```text
indisponible al menos 2 días
```

y vuelve a operación durante el turno:

```text
15:00 – 23:00
```

Además, la actualización posterior indica:

- la Tipo 3 permanece en el lugar de la avería por 4 horas;
- las unidades con averías Tipo 2 y Tipo 3 son llevadas de manera instantánea al almacén central luego del tratamiento correspondiente;
- los paquetes que no hayan sido trasvasados también pueden terminar siendo llevados al almacén central.

### ACTUALIZACIÓN MÁS RECIENTE

Las averías:

> **NO se registrarán mediante archivo.**

Se introducirán **manualmente** en:

```text
Día a Día
Simulación 5D
```

Para la experimentación numérica inicial no deben tratarse como un dataset automático de averías.

---

# 14. Trasvase — NUEVA REGLA

**Trasvase** significa transferir los productos desde una unidad averiada hacia otra unidad de transporte.

Cuando llega una nueva unidad para recoger la carga:

- puede recoger los productos;
- después puede hacer un giro en U para regresar por donde llegó;
- o puede continuar su recorrido sin regresar, según corresponda.

Debe considerarse un:

```text
tiempo de trasvase = 30 minutos
```

Esta regla es importante para futuras replanificaciones frente a averías.

---

# 15. Escenarios del sistema

PaqRap debe trabajar con tres escenarios.

## Día a Día

- operación en tiempo real;
- pedidos registrados manualmente;
- averías registradas manualmente;
- planificación/replanificación durante la operación.

## Simulación 5D

- representa cinco días;
- ejecución acelerada;
- se muestran hora real y hora simulada;
- puede recibir averías manuales;
- utiliza datos de archivos.

La referencia del curso ha sido comprimir los cinco días dentro de aproximadamente:

```text
30–60 minutos
```

## Colapso

Se ejecutan los datos disponibles hasta llegar al momento en que ya no exista una planificación que permita atender la demanda dentro de los plazos.

Importante:

```text
pedido temporalmente no asignado
≠ automáticamente colapso
```

Colapso significa que la demanda pendiente **ya no puede ser atendida respetando los plazos**.

---

# 16. Algoritmos

Se seleccionaron:

```text
GRASP
Simulated Annealing
```

No se utilizarán Búsqueda Tabú ni Algoritmo Genético.

---

# 17. Arquitectura común de los algoritmos

Las versiones actuales fueron unificadas para trabajar sobre el mismo dominio.

Conceptualmente:

```text
OperationalSnapshot
        ↓
   OperationalPlan
        ↓
 ┌──────┴──────┐
GRASP           SA
 └──────┬──────┘
        ↓
OperationalPlanEvaluator
        ↓
PlanEvaluation
```

Principales estructuras compartidas:

```text
OperationalSnapshot
OperationalPlan
DeliveryRoute
RouteStop
DeliveryStop
WarehouseVisit
RoadNetwork
RoadPath
RouteScheduler
OperationalPlanEvaluator
PlanEvaluation
Order
Vehicle
Warehouse
RoadBlock
```

`OperationalPlanEvaluator` debe ser la **única autoridad común para determinar la factibilidad**.

---

# 18. GRASP

La implementación realiza:

```text
1. Construcción greedy-aleatorizada
2. Restricted Candidate List (RCL)
3. selección aleatoria controlada por α
4. búsqueda local
5. comparación con la mejor solución
```

Parámetros principales:

```text
alpha
maxIteraciones
semilla
```

Una referencia actual usada:

```text
alpha = 0.3
```

El costo se minimiza **solo entre soluciones que cumplen las restricciones necesarias**.

---

# 19. Simulated Annealing

SA trabaja mediante:

```text
1. creación de solución inicial factible
2. generación de vecino
3. evaluación
4. aceptación mediante criterio de Metropolis
5. reducción de temperatura
6. repetición
```

Configuración típica:

```text
temperatura inicial
temperatura mínima
factor de enfriamiento
iteraciones por temperatura
máximo de iteraciones
máximo sin mejora
```

El generador de vecinos puede incluir operaciones como:

```text
relocation
swap
2-opt
split delivery
open route
close route
change warehouse
```

---

# 20. EXPERIMENTACIÓN NUMÉRICA — OBJETIVO ACTUAL

Esta es actualmente una de las tareas principales.

Se desea comparar:

```text
GRASP vs Simulated Annealing
```

en términos de:

### Efectividad

```text
factibilidad
porcentaje de pedidos atendidos
soluciones completas
```

### Eficiencia / desempeño

```text
costo total
distancia
tiempo de cómputo
```

Ambos algoritmos deben ejecutarse:

- sobre las mismas instancias;
- con la misma flota;
- mismos pedidos;
- mismos bloqueos;
- mismo dominio;
- mismo evaluador;
- mismas restricciones.

---

# 21. Condiciones actualizadas de la experimentación

### Se debe considerar

```text
Pedidos ✅
Flota ✅
Almacenes ✅
Capacidades ✅
Velocidades ✅
Costos ✅
Plazos ✅
Bloqueos ✅
Red vial ✅
```

### No considerar por ahora

```text
Mantenimiento preventivo ❌
Averías automáticas por archivo ❌
Frontend ❌
Backend REST ❌
Persistencia ❌
Docker ❌
```

Las averías pertenecen más a la simulación del producto final y serán registradas manualmente en Día a Día y 5D.

---

# 22. Regla fundamental para comparar algoritmos

No basta con comparar costo.

Se debe definir:

```java
solucionCompleta =
    resultado.esFactible()
    && resultado.noAtendidos().isEmpty();
```

Ejemplo:

```text
GRASP
Costo S/300
8/10 atendidos

SA
Costo S/500
10/10 atendidos
```

No se puede afirmar que GRASP sea mejor por costar menos.

El orden correcto de evaluación es:

```text
1. ¿La solución es completa?
2. ¿Es factible?
3. Cobertura
4. Costo
5. Distancia
6. Tiempo computacional
```

---

# 23. Igualdad de condiciones experimentales

No se recomienda comparar:

```text
GRASP 500 iteraciones
SA 500 iteraciones
```

porque una iteración no representa el mismo esfuerzo computacional.

La comparación principal debería usar:

```text
mismo presupuesto máximo de tiempo
```

Ejemplo:

```text
GRASP → 10 s
SA    → 10 s
```

o el valor que se determine mediante una prueba piloto.

La construcción inicial de SA debe formar parte de ese presupuesto.

---

# 24. Semillas

Debe controlarse la aleatoriedad.

Cada resultado experimental debe identificar:

```text
instancia
algoritmo
semilla
```

Las semillas deben repetirse de forma controlada y registrarse para permitir reproducibilidad.

GRASP debe crear un planificador nuevo por corrida para no reutilizar el estado interno de `Random`.

---

# 25. Resultado experimental uniforme

Se quiere crear un `ExperimentRunner`.

Cada corrida debería generar algo similar a:

```text
instancia
algoritmo
semilla

factible
completa

pedidosTotales
pedidosAtendidos
pedidosNoAtendidos

costoTotal
distanciaTotal

tiempoComputo

rutasGeneradas
violaciones

estadoFinal
```

Posibles estados:

```text
COMPLETE
INCOMPLETE
NO_SOLUTION
TIMEOUT
ERROR
```

Los resultados deben exportarse automáticamente a:

```text
CSV
```

para análisis estadístico posterior.

---

# 26. Piloto experimental

Antes de la campaña final se debe hacer un piloto.

Ejemplo:

```text
5 pedidos
10 pedidos
15 pedidos
20 pedidos
...
```

para determinar:

- tiempo de ejecución;
- escalabilidad;
- límite de tiempo adecuado;
- configuración razonable de GRASP y SA.

Después se realizará la campaña experimental definitiva.

---

# 27. Análisis estadístico posterior

La comparación debería utilizar instancias pareadas:

```text
misma instancia
GRASP
SA
```

Luego analizar:

```text
cobertura
factibilidad
costo
distancia
tiempo
```

No debe copiarse directamente la función objetivo con penalizaciones del documento de otro grupo.

En PaqRap los plazos y capacidades son fundamentalmente **restricciones**, no simplemente penalizaciones monetarias.

---

# 28. Backend futuro

El backend final está previsto con:

```text
Java 21
Spring Boot
Maven
Spring Data JPA
Hibernate
MySQL 8
```

Arquitectura conceptual:

```text
Frontend
   ↓ REST/JSON
Controller
   ↓
Service
   ↓
Dominio / Planificación
   ↓
GRASP / SA
   ↓
OperationalPlanEvaluator
   ↓
Persistencia
```

Los algoritmos **no deben consultar directamente MySQL**.

---

# 29. Frontend actual/futuro

Stack:

```text
React 19
TypeScript
Vite
Tailwind CSS
shadcn/ui
Axios
React Hook Form
Zod
React Konva / Konva.js
```

React Konva se eligió porque el mapa es una retícula artificial, no un mapa geográfico.

Frontend actual tiene avances en:

```text
Configuración
Ejecución
Mapa
Panel de indicadores
Datos mock
```

Por ahora no debe mezclarse con la experimentación numérica.

---

# 30. Docker

Se plantea:

```text
Docker
```

para despliegue futuro:

```text
Frontend
Backend
MySQL
```

pero no es necesario para la experimentación numérica.

---

# 31. Modelo de dominio conceptual

Módulos principales:

```text
1. Pedidos y Clientes
2. Almacenes e Inventario
3. Flota y Operación
4. Red Vial e Incidencias
5. Planificación y Rutas
6. Algoritmos Metaheurísticos
7. Escenarios de Simulación
8. Visualización e Integración
```

En implementación, el núcleo más importante es:

```text
Dominio Operativo Compartido
Red Vial
Planificación y Rutas
GRASP
Simulated Annealing
```

---

# 32. Estado actual más importante

El trabajo inmediato **NO es terminar todo PaqRap**.

La prioridad actual es:

> **dejar GRASP y Simulated Annealing completamente preparados para realizar una experimentación numérica justa, reproducible y bajo condiciones equivalentes.**

El foco debe estar en:

```text
dominio común
+
evaluador común
+
instancias comunes
+
bloqueos
+
semillas
+
tiempo común
+
métricas uniformes
+
CSV
+
análisis posterior
```

Ignorar temporalmente:

```text
frontend
REST
MySQL
Docker
escenarios completos de producción
```

---

# 33. Últimas aclaraciones del profesor — PRIORIDAD ALTA

Estas son las actualizaciones más recientes y deben prevalecer sobre interpretaciones anteriores:

### Trasvase

Es el traslado de productos:

```text
unidad averiada
→ nueva unidad de transporte
```

El trasvase toma aproximadamente:

```text
30 minutos
```

Después, la nueva unidad puede:

- regresar mediante giro en U por donde llegó, o
- continuar su marcha, según la ruta.

### Experimentación numérica

```text
mantenimiento preventivo → NO considerar
bloqueos → SÍ considerar
```

### Averías

```text
NO se cargan mediante archivo
```

Serán registradas manualmente en:

```text
Día a Día
Simulación 5D
```

---

## TEXTO CORTO PARA PEGAR AL INICIO DE OTRO CHAT

Si necesitas una versión mucho más corta para iniciar otra conversación, puedes pegar esto:

> Estoy desarrollando **PaqRap**, un sistema logístico de PUCP para distribuir un único producto P mediante autos, motos y bicicletas sobre una retícula simulada de 70×50 km. Los algoritmos seleccionados son **GRASP y Simulated Annealing**, ambos implementados en Java y unificados sobre un dominio compartido (`OperationalSnapshot`, `OperationalPlan`, `RoadNetwork`, `RouteScheduler`, `OperationalPlanEvaluator`, etc.).
>
> La prioridad actual es realizar una **experimentación numérica justa y reproducible entre GRASP y SA**, ignorando por ahora frontend, REST, BD y Docker. Ambos deben usar las mismas instancias, flota, inventarios, velocidades, costos, plazos, red vial y bloqueos, y ser evaluados por el mismo `OperationalPlanEvaluator`.
>
> Para el experimento, **sí se consideran bloqueos y no se considera mantenimiento preventivo**. Las averías no vienen de archivo; en el producto final se registrarán manualmente en Día a Día y Simulación 5D. Las entregas parciales están permitidas y cada entrega consume 1 h de acondicionamiento fuera del plazo. Los almacenes vigentes son Central `(27,14)`, Nor-Oeste `(12,38)` y Este `(57,27)`.
>
> Flota actual: 10 autos (cap. 24, 40 km/h, S/8/km), 15 motos (cap. 8, 25 km/h, S/6/km), 12 bicicletas (cap. 4, 12 km/h, S/3/km). Todos parten del Central.
>
> La comparación debe priorizar: solución completa/factible → cobertura → costo → distancia → tiempo. No se deben comparar simplemente iguales números de iteraciones; se busca usar el mismo presupuesto computacional. Se debe crear un `ExperimentRunner`, controlar semillas, registrar timeouts/fallos y exportar cada corrida a CSV.
>
> Actualización reciente: existe **trasvase** de carga desde una unidad averiada hacia otra; toma 30 min. Tras el trasvase, la unidad receptora puede regresar mediante giro en U o continuar la ruta según corresponda.

Ese sería el contexto que yo usaría como **fuente de verdad para continuar el proyecto en otro chat**.