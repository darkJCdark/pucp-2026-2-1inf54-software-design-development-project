# Limitaciones y decisiones que se preservaron

## Alcance de la unificación

Este laboratorio compara dos implementaciones concretas con un mismo evaluador, no implementa todos los requisitos pendientes de PaqRap. Compartir dominio elimina diferencias de reglas entre copias; no demuestra por sí mismo que el dominio cumpla perfectamente el caso.

## GRASP y pedidos divididos (resuelto en la versión 2)

En la versión 1, `GraspPlanificador` no generaba entregas parciales: un pedido mayor que la mayor capacidad (24) quedaba siempre sin atender, y la familia SPLIT lo exponía. La P&R 13 del curso admite entregas parciales, así que la versión 2 (`GRASP-v2 2026-09-23`) lo corrige de forma explícita y versionada (ver `CAMBIOS_Y_PROCEDENCIA.md`): reparte ese pedido en partes que caben en un vehículo, todas o ninguna. Solo se divide cuando el pedido no cabe entero en ningún vehículo; GRASP no explora divisiones "por conveniencia" de pedidos que sí caben. No comparar resultados SPLIT de la versión 1 con los de la versión 2.

## Ambos algoritmos terminan de forma distinta dentro del presupuesto

El esquema de enfriamiento de SA (temperatura 1000 → 1, factor 0,95, 50 vecinos por nivel: 6.750 iteraciones) termina por temperatura y puede acabar antes de presupuestos de 5 s o más; GRASP es un multiarranque que suele usar todo el presupuesto. El protocolo compara a igual tiempo **máximo**, no a igual tiempo **usado**: revisar `elapsed_ms` y `termination`. Si se quiere comparar a igual tiempo usado, calibrar en el piloto el esquema de SA (y con esfuerzo comparable los parámetros de GRASP) antes de la campaña formal; la versión 4 no cambió esos parámetros.

## Pedidos no atendibles, refrigerio y ventanas largas

Con ventanas reales largas aparecen pedidos que ningún plan puede cubrir: (a) ya vencidos al planificar, que ahora se excluyen y registran en el manifiesto; (b) otros que ni una ruta directa desde el central llega a tiempo, que la auditoría señala en `orders_provably_unservable`. Un caso frecuente de (b) proviene de la regla heredada del refrigerio: un vehículo que sale después de las 08:00 toma la hora de refrigerio en su **primera** llegada, aunque acabe de salir, y eso lo hace llegar tarde a pedidos con plazo corto (verificado con 3 pedidos del 13-sep, ventana 07:00–11:00). Es una regla del dominio común, igual para ambos algoritmos; su conformidad con el enunciado debe validarla el equipo (ver abajo).

Desde SA v1.2, la semilla conserva los pedidos que pudo admitir y separa los restantes como `unattended`. La búsqueda optimiza solo el subconjunto atendido y la auditoría común, aplicada a toda la demanda original, clasifica el resultado como `PARTIAL`. `NO_INITIAL_PLAN` queda para el caso en que no se admite ningún pedido y no es un certificado de imposibilidad.

## SA: solución inicial y vecindarios

SA v1.2 parte de una semilla factible para los pedidos admitidos. `InitialPlanBuilder` está fuera del optimizador y ordena por deadline con desempate por identificador; usa round-robin como preferencia, prueba las demás rotaciones en orden determinista y conserva el primer candidato factible, no el de menor costo. Cada pedido —incluidos sus `DeliveryStop` parciales— se acepta completo o no se incorpora. Esta mejora heurística no demuestra que el conjunto `unattended` sea imposible ni busca maximizar cobertura global. Su tiempo está dentro del presupuesto.

La vecindad original contiene movimientos que cambian entregas sin reparar siempre cantidades recogidas en visitas de almacén. El evaluador rechaza esos candidatos. Se contabilizan los intentos y vecinos inválidos; no se sustituyó la vecindad por la de GRASP. Por tanto una baja mejora también puede ser consecuencia de esta implementación de operadores.

## Exclusiones del diseño experimental

Por aclaración docente, `smoke`, `escalabilidad`, `pilot` y `formal` incluyen bloqueos pero no mantenimiento preventivo ni averías. La exclusión se hace al construir la instancia; el dominio general conserva ambas capacidades y sus pruebas. La antigua familia `REDUCED`, basada en mantenimiento, dejó de formar parte de los CSV activos.

Las averías manuales, el trasvase de productos y la decisión de que la unidad de apoyo gire en U o continúe pertenecen a futuros escenarios operacionales día a día/5D. No están implementados en este laboratorio y no deben inferirse de estos resultados.

## No atendidos, factibilidad y colapso

El resultado nativo de GRASP puede evaluar positivamente las rutas construidas usando solo los pedidos que incluyó. La auditoría experimental vuelve a evaluar contra TODA la demanda original y exporta por separado la validez de las rutas y la cobertura completa.

Un resultado parcial, un timeout, un error y un fallo del constructor inicial son resultados diferentes. Ninguno demuestra que no exista una solución factible. Aquí no se calcula un instante de colapso logístico; solo se observa si se halló una solución dentro de un presupuesto.

## Reglas heredadas que requieren contraste con el caso

- **Refrigerio:** el `RouteScheduler` compartido programa una hora de alimentación según su política original. El código no constituye una demostración de cumplimiento de todos los márgenes y casos de la ventana de refrigerio. En particular, la programación tardía de una pausa requiere revisar el límite superior de esa ventana. Se preservó el comportamiento para ambos algoritmos, sin certificarlo como cumplimiento completo del requisito.
- **80 km:** el evaluador heredado limita a 80 km el desplazamiento entre paradas. El propio README de GRASP vincula esta decisión con la interpretación de la hoja de flota. Debe confirmarse si la regla de negocio se refiere a esa magnitud; no se presenta como una verdad independiente de los archivos del equipo.
- **Coordenadas:** por defecto se usan Central `(27,14)`, Nor-Oeste `(12,38)` y Este `(57,27)`, porque así aparecen en los últimos fuentes/README entregados. No se cambian a las coordenadas de versiones anteriores de UML. Son configurables salvo Nor-Oeste, que permanece como en el código.
- **Estados e inventario:** se parte del estado inicial de cada instancia. No se ejecutan rutas en el tiempo ni se registra consumo real continuo. Los inventarios son evaluados sobre planes y cronogramas.
- **Retorno y abastecimiento:** se mantienen las reglas originales del evaluador, incluyendo los tipos de violación de retorno. No se reemplazan por simplificaciones del prototipo TypeScript.

## Medición y reproducibilidad

Los límites son cooperativos: un cálculo puede finalizar unos milisegundos después del tope. El límite externo de la JVM evita esperas indefinidas y exporta el fallo. El muestreo de heap es aproximado. `ActiveProcessorCount` no reserva ni limita físicamente dos CPU.

Una semilla fija con modo FIXED, mismas entradas y mismo entorno produjo planes idénticos en las comprobaciones incluidas. Con modo TIME, el prefijo de búsqueda alcanzado depende del rendimiento del equipo: no se promete idéntico plan bit a bit entre máquinas o corridas temporizadas.

## Datos, inicialización y objetivos

Los costos y entregas son planificados, no indicadores realizados del simulador. Los sintéticos tienen distribución deliberadamente sencilla y no se presentan como representativos estadísticos de toda la demanda real. Los datos reales son lotes conocidos al final de la ventana de recogida; esto evita anticipar pedidos, pero no reproduce la operación online.

Los parámetros iniciales no fueron optimizados. El piloto y la campaña formal no se ejecutaron al elaborar el entregable. La prueba smoke y la mini campaña v4 de 24 corridas son validación previa, no permiten declarar un ganador general.

## Compilación y compatibilidad

Versión 1: se verificaron compilación con JDK 21, lanzamiento del JAR y pruebas en Linux, sin `mvn`. Versión 2: en Windows 11 con JDK 21 se ejecutaron `mvn clean verify` (22 pruebas JUnit), `scripts/build.sh` (Git Bash) y el JAR resultante; `scripts/build.ps1` no se ejecutó. Versión 4: en Windows 10, Java/Javac 22 con `--release 21`, se ejecutaron 38 pruebas JUnit y `scripts/build.ps1`. El `mvn clean verify` literal falló antes de compilar porque el entorno apuntó a `C:\.m2\repository`; con un settings temporal que señala el repositorio local existente, `mvn -s ... -o clean verify` terminó correctamente. Con Maven, usar siempre `clean`: sin él, el `maven-shade-plugin` puede mezclar clases viejas con nuevas. No se incluyeron dependencias de `.m2`.
