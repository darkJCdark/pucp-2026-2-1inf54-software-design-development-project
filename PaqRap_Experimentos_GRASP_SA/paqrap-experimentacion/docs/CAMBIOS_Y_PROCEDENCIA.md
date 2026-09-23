# Cambios y procedencia

## Base utilizada

Se partió de los dos ZIP proporcionados por el usuario, identificados en `provenance/archives.sha256`. No se utiliza como evidencia la afirmación anterior del chat de que habían sido probados: la carpeta `evidencia/` corresponde a verificaciones efectivamente realizadas sobre este entregable.

| Destino | Origen |
|---|---|
| `dominio/.../domain` y `dominio/.../route` | Módulo `dominio` del último ZIP GRASP |
| `grasp/.../GraspPlanificador` | Implementación GRASP del ZIP |
| `grasp/.../ResultadoPlanificacion` | Resultado nativo GRASP del ZIP |
| `sa/...` | Familia Operational de SA: optimizador, generador de vecinos, interfaz, configuración y resultado |
| `experimentos/.../modelo` | Cargadores y auxiliares de entrada del ZIP GRASP |
| `data/` | Archivos originales de entrada del ZIP GRASP |
| `experimentos/.../experiment` | Nuevo ejecutor experimental y adaptadores |
| `dominio/.../search` | Nueva instrumentación opcional por hilo |

No se copió el segundo `domain/route` que venía con SA. Su código se compila contra el dominio actualizado compartido. Se conservaron algunos auxiliares heredados en el dominio para compatibilidad y pruebas; la campaña invoca `OperationalPlan`, `OperationalPlanEvaluator` y la familia Operational de SA, no el optimizador SA antiguo sobre `RoutePlan`.

## Cambios instrumentales sobre los algoritmos y dominio

1. **Tiempo y contadores opcionales.** Se agregó `SearchControl`, instalado exclusivamente durante una corrida. Comprueba plazo en ciclos, evaluador, scheduler y red; cuenta evaluaciones, caminos y vecinos. Fuera del laboratorio las APIs pueden usarse sin instalarlo.
2. **Mejor resultado ante interrupción.** GRASP conserva la construcción ya evaluada antes de comenzar búsqueda local, para no perderla si vence el plazo durante esa fase. SA conserva el mejor plan inicial/vecino aceptado cuando se interrumpe la búsqueda. Una excepción técnica de detención no se confunde con una violación de reglas.
3. **Orden determinista.** Los mapas compartidos se construyen con orden estable. La cola de caminos añade desempates por coordenadas. Esto puede cambiar elecciones entre empates respecto del repositorio original, por lo que se explicita y aplica a ambos.
4. **Validación inicial GRASP.** Se rechazan iteraciones no positivas y alpha fuera de rango/no finito. No se convierten configuraciones erróneas en éxitos de costo cero.
5. **Trazas de mejora.** Se registra primera solución completa y mejoras costo/cobertura. No se utiliza información del resultado del otro algoritmo.
6. **Compilación unificada.** Los cuatro módulos usan release Java 21. No se introduce Spring ni persistencia.

Los parches exactos sobre fuentes previas están en `provenance/cambios.patch`. El manifiesto `origen_archivos.json` distingue archivos originales conservados, modificados y añadidos.

## Adaptadores: una entrada/salida común, dos implementaciones reales

```text
InstanceFactory -> ProblemInstance
                       |
              UnifiedPlanner.solve(...)
                 /                   \
       GraspAdapter                 SaAdapter
            |                          |
   GraspPlanificador          InitialPlanBuilder original
            |                          |
 ResultadoPlanificacion       OperationalPlan inicial
                                       |
                          OperationalSimulatedAnnealingPlanner
                                       |
                            OperationalAnnealingResult
                 \                   /
                     AlgorithmOutput
                           |
                   CommonAudit (TODOS los pedidos)
                           |
                    CSV / JSON / HTML
```

GRASP mantiene `planificar(...)`. SA mantiene `optimize(...)`. Los adaptadores normalizan salida y protocolo sin exigir que sus firmas originales sean idénticas ni afirmar que ya implementaban una interfaz Strategy común.

El constructor inicial de SA se toma del núcleo compartido entregado. No depende de GRASP, no recibe gratuitamente un plan elaborado por este y su costo de construcción se mide.

## Cambios que NO se hicieron

No se añadieron microservicios, backend HTTP, frontend, algoritmos nuevos, penalizaciones de incumplimiento, un solucionador exacto, operadores de división a GRASP o reparaciones automáticas de carga a SA. No se alteraron ocultamente refrigerio, bloqueos, capacidades o mantenimiento para forzar factibilidad.

## Lectura sugerida del código

`ExperimentMain` organiza parejas y procesos. `InstanceFactory` materializa el escenario. `TrialMain` delimita medición y auditoría. `GraspAdapter` y `SaAdapter` llaman a los algoritmos existentes. `CommonAudit` decide cobertura y factibilidad comparable. `HtmlReport` y `analyze.py` preparan los resultados.

## Para integrar después al backend

El backend podrá depender de `paqrap-dominio`, `paqrap-grasp` y `paqrap-sa`. `experimentos` es un consumidor de esos módulos y no una dependencia que el motor necesite. Las adaptaciones que correspondan al negocio final deben hacerse y probarse en el dominio común, no por duplicado en los algoritmos.
