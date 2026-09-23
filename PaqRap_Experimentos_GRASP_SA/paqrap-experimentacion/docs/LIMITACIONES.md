# Limitaciones y decisiones que se preservaron

## Alcance de la unificación

Este laboratorio compara dos implementaciones concretas con un mismo evaluador, no implementa todos los requisitos pendientes de PaqRap. Compartir dominio elimina diferencias de reglas entre copias; no demuestra por sí mismo que el dominio cumpla perfectamente el caso.

## GRASP y pedidos divididos

El `GraspPlanificador` entregado busca candidatos que admitan la cantidad del pedido en una unidad y no construye sistemáticamente entregas parciales para demandas superiores a la mayor capacidad (24). Sus pruebas originales incluyen un pedido de 30 que queda sin atender. El dominio sí permite `DeliveryStop` parciales, pero que el modelo pueda representarlos no implica que GRASP los genere.

La familia SPLIT conserva esta situación a propósito: es una prueba de efectividad de la implementación, no una comparación de costo sobre soluciones equivalentes. Si GRASP deja demanda sin cubrir, no se usa su menor costo como ventaja. Agregar un operador de división sería una modificación algorítmica separada que debe versionarse y volver a evaluarse; no se hizo silenciosamente.

## SA: solución inicial y vecindarios

SA parte de un plan inicial completo y factible. El `InitialPlanBuilder` original está fuera del optimizador; construye un plan determinista y puede fallar aun cuando exista otro. Su tiempo está dentro del presupuesto. `NO_INITIAL_PLAN` no es un certificado de imposibilidad.

La vecindad original contiene movimientos que cambian entregas sin reparar siempre cantidades recogidas en visitas de almacén. El evaluador rechaza esos candidatos. Se contabilizan los intentos y vecinos inválidos; no se sustituyó la vecindad por la de GRASP. Por tanto una baja mejora también puede ser consecuencia de esta implementación de operadores.

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

Los parámetros iniciales no fueron optimizados. El piloto y la campaña formal no se ejecutaron al elaborar el entregable. La prueba smoke incluida no permite declarar un ganador general.

## Compilación y compatibilidad

Se verificaron compilación con JDK 21, lanzamiento del JAR y pruebas en Linux. El POM Maven se proporciona, pero no se ejecutó `mvn` en ese entorno. Los scripts Windows fueron redactados y revisados, no ejecutados en Windows. No se incluyeron binarios de terceros de las carpetas `.m2` ni archivos compilados antiguos de los ZIP.
