# PaqRap · laboratorio GRASP y Simulated Annealing · v3
##IMPORTANTE: La exp numerica es un proyecto aislado, no mergear con main, ya luego lo integraremos, por ahora que se quede aislado :v

**Entrega corregida: 24 de septiembre de 2026.** Módulo autónomo Java 21 para experimentación numérica. No requiere frontend, REST, base de datos, Docker ni Maven para compilar y ejecutar con los scripts incluidos.

La versión modifica **ambos algoritmos** y su dominio compartido. No mezclar sus resultados con `evidencia/` histórica o `evidencia/v2/`. Las verificaciones de esta entrega están exclusivamente en **`evidencia/v3/`**.

## Empezar en Windows

Descomprimir y abrir una terminal **dentro de esta carpeta `paqrap-experimentacion`**, no en el backend ni en la raíz superior del repositorio.

```powershell
java -version
java -Xmx512m -jar dist/paqrap-experimentos.jar --self-test
java -jar dist/paqrap-experimentos.jar --config config/readiness.properties --output results/mi-piloto-v3
```

El JAR incluido requiere Java 21 o superior. Para recompilar después de modificar código, se necesita un **JDK** con `javac` y `jar`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1
java -Xmx512m -jar dist/paqrap-experimentos.jar --self-test
```

En Linux/macOS:

```bash
bash scripts/build.sh
java -Xmx512m -jar dist/paqrap-experimentos.jar --self-test
bash scripts/test-road-network.sh
java -jar dist/paqrap-experimentos.jar --config config/readiness.properties --output results/mi-piloto-v3
```

No ejecutar dos campañas simultáneamente si se van a comparar tiempos. La carpeta de salida debe ser nueva. Para continuar una ejecución interrumpida con **exactamente el mismo JAR, datos, configuración y entorno**:

```powershell
java -jar dist/paqrap-experimentos.jar --config config/readiness.properties --output results/mi-piloto-v3 --resume true
```

Una recompilación puede cambiar la huella del JAR, incluso sin cambios funcionales. En ese caso corresponde una carpeta nueva, no forzar la reanudación. Las rutas absolutas registradas en los `jobs` de la evidencia son del entorno de verificación; no deben ejecutarse directamente en otra computadora.

## Qué está corregido

Los dos algoritmos comparten `OperationalSnapshot`, `RouteScheduler`, `OperationalPlanEvaluator`, reconstrucción de cargas y validación cronológica de inventario. GRASP conserva construcción greedy-aleatorizada con RCL y búsqueda local. SA conserva vecinos y aceptación de Metropolis, aplicada al costo solo cuando no cambia la cobertura.

- **Bloqueos sí; mantenimiento preventivo y averías automáticas no.** La familia REDUCED reduce realmente la flota antes de la corrida.
- Plazos duros; llegada física separada de la hora de acondicionamiento. Entregas parciales y recargas conservan cantidades y respetan capacidad.
- SA ya no descarta todos los pedidos si uno no puede insertarse. Los dos conservan el mejor plan validado al agotarse el tiempo.
- Los movimientos de SA y GRASP ajustan cargas y recargas; toda propuesta se valida sobre la demanda original completa.
- Presupuesto común de tiempo, incluyendo inicialización de SA; semillas explícitas y planificador nuevo por corrida. SA recalienta en modo TIME al llegar a la temperatura mínima.
- CSV, planes JSON con rutas y cronología, trazas, huellas de entrada, informe HTML y reanudación verificada.

**No se convierte un plan parcial en éxito completo ni se interpreta como colapso por sí solo.** `cost_complete_feasible` queda vacío cuando el plan no cubre toda la demanda válidamente.

## Políticas del modelo que debes conocer

La descripción maestra no fija la hora exacta del refrigerio ni cuantifica sus márgenes. Se declaró una política común y configurable: una hora por turno, con inicio a los 180 minutos del turno (10–11, 18–19 y 02–03). Se conservan márgenes de una hora del código previo; no se presentan como una nueva aclaración del profesor. `meal.startOffsetMinutes` permite otro inicio entre 60 y 360 minutos.

El límite heredado de 80 km por tramo **no aparece en el contexto maestro**. Por eso `routing.maxLegKm=0` lo desactiva en la línea base. Se puede declarar una sensibilidad con `80`; no cambiarlo después de observar resultados para favorecer un algoritmo.

Los lotes reales se planifican al final de la ventana de recepción. Esto es una **comparación estática**, no una simulación online: por defecto se conservan pedidos ya vencidos (`orders.expiredPolicy=KEEP`). La alternativa `EXCLUDE` es explícita y registra los identificadores excluidos. No mezclar ambas poblaciones.

## Configuraciones disponibles

| Perfil | Propósito | Corridas configuradas |
|---|---|---:|
| `smoke` | Integración corta: normal, bloqueado, dividido y real | 16 |
| `readiness` | 5, 10, 15 y 20 pedidos con bloqueos; lote real de 41 pedidos | 10 |
| `deterministic` | Repetibilidad FIXED, no comparación temporal | 4 por ejecución |
| `pilot` | Piloto más amplio para calibrar parámetros | 72 |
| `escalabilidad` | Volúmenes sintéticos y ventanas reales mayores | 96 |
| `formal` | Plantilla de campaña; **calibrar y congelar antes de usar** | 400 |

Los números de corrida son los de los archivos entregados, no una prescripción estadística. Las campañas `pilot`, `escalabilidad` y `formal` no se ejecutaron completas con v3 en esta entrega. Los datos originales de mantenimiento se conservan en `data/` por procedencia, pero el constructor experimental no los carga.

## Resultados y verificación

Cada salida contiene `runs.csv`, `paired.csv`, `report.html`, `metadata.json`, `instances/` y `jobs/`. El costo pareado se calcula únicamente cuando **ambos** algoritmos completan la misma instancia y repetición.

Análisis descriptivo opcional con Python, sin paquetes adicionales:

```powershell
python scripts/analyze.py results/mi-piloto-v3
```

Se verificaron 76 comprobaciones ejecutables, 500 escenarios diferenciales de red vial, 16 corridas smoke, 10 de readiness, repetibilidad en dos campañas FIXED y reanudación segura. Ver números y limitaciones en **`docs/VERIFICACION.md`**. Esta evidencia valida el funcionamiento probado, no optimalidad ni un ganador general.

Documentación: `docs/CAMBIOS_Y_PROCEDENCIA.md`, `docs/PROTOCOLO.md`, `docs/GUIA_RESULTADOS.md` y `docs/LIMITACIONES.md`.
