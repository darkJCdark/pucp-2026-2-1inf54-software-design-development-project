# Alcance y límites de la versión entregada

- El descanso es obligatorio y variable. La selección de su horario utiliza una búsqueda acotada
  de alternativas, no optimización exacta continua. No encontrar una solución no prueba inexistencia.
- El margen de una hora se aplica al intervalo completo del descanso respecto de ambos extremos
  del turno. Confirmar esa interpretación con el docente si su aclaración tiene un significado distinto.
- La situación anterior al snapshot se modela como inactividad en Central; un descanso legal previo
  se registra explícitamente. No se reconstruye un historial real de conductores de 5D.
- Cada ruta contiene descansos de sus turnos cubiertos. Los vehículos sin ruta tienen un registro
  ocioso del primer turno, no un calendario completo de cinco días.
- El laboratorio trabaja por lotes; no inyecta pedidos durante el presupuesto ni integra incidencias
  manuales, trasvases o ejecución continua. Eso pertenece al sistema completo.
- Las velocidades experimentales configurables se validan en 1..1000 km/h. La base del caso usa
  40/25/12. No se ha diseñado para aristas cuya conducción continua supere una hora.
- El límite temporal de búsqueda es cooperativo, no un sistema de tiempo real duro. Hay guardia
  adicional por proceso; el costo de JVM/calentamiento/auditoría no está dentro del presupuesto.
- La calidad puede deteriorarse con mucha demanda o presupuestos muy cortos. Los nuevos resultados
  muestran que programar descansos flexibles requiere recalibrar el tiempo; no reutilizar cifras antiguas.
- No se ejecutaron el piloto completo de 288 corridas ni la campaña formal de 400. Se incluyen
  configuraciones y herramientas para ejecutarlos y analizarlos, además de un minipiloto técnico.
- Las verificaciones se realizaron con Java 21.0.11/Linux. No se ejecutaron PowerShell/Windows,
  Maven/Surefire ni el motor JUnit. Sí se compilaron y ejecutaron los cuerpos de pruebas Java con
  un adaptador offline explícito; esto no se presenta como una ejecución del motor JUnit.
- No se probaron instalaciones de dependencias opcionales de gráficos/estadística. Las herramientas
  principales Java y Python de auditoría/tablas/congelación funcionan sin esas dependencias.
- No se debe comparar directamente con campañas de descanso fijo, código previo o subconjuntos
  de demanda distintos, ni asignar una función distinta a cada algoritmo en 5D a partir de estas pruebas.
