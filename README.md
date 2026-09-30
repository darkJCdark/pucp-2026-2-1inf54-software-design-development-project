# PaqRap — Experimentación numérica con descanso flexible (flex1)

Esta carpeta es autónoma: contiene dominio, GRASP, Simulated Annealing, ejecutor experimental,
datos, pruebas y herramientas de análisis. No necesita frontend, REST, MySQL, Docker ni modo 5D.
Se entrega el código fuente y `dist/paqrap-experimentos.jar`, compilado para Java 21.

## Cambio principal

El descanso de alimentación es **obligatorio, continuo y de 60 minutos por turno**, pero **no tiene
una hora fija para toda la flota**. El programador común explora distintas ubicaciones temporales
del descanso para cada ruta. Puede aprovechar esperas, descansar antes/después de una entrega o
al volver al almacén. Nunca cuenta la conducción o el acondicionamiento como descanso.
Se registran lugar, turno, inicio y fin; un auditor independiente comprueba las restricciones.

Se conserva una hora de margen después del inicio y antes del fin del turno. Por ejemplo,
en 07:00–15:00 el descanso puede INICIAR entre 08:00 y 13:00 y debe terminar a más tardar a las 14:00.
La interpretación y el estado inicial de descanso de los lotes están en `docs/DESCANSO_FLEXIBLE.md`.

**No reutilizar los resultados antiguos de descanso fijo.** Los datos de evidencia incluidos se
volvieron a ejecutar con este JAR. No son una campaña formal ni una demostración de superioridad.

## Empezar en Windows (PowerShell)

Descomprime el ZIP. Abre la terminal dentro de `paqrap-experimentacion`, donde están
`dominio`, `grasp`, `sa`, `experimentos`, `config` y `dist`.

```powershell
java -version
java -Xmx512m -jar dist/paqrap-experimentos.jar --self-test
java -jar dist/paqrap-experimentos.jar --config config/readiness.properties --output results/readiness-01
```

Se necesita Java 21 o superior. Abre `results/readiness-01/report.html` al finalizar.
El reporte y los archivos JSON contienen los descansos y la validez de las rutas.

Para validar las entradas sin ejecutar las búsquedas:

```powershell
java -jar dist/paqrap-experimentos.jar --config config/pilot.properties --validate-only true
```

## Piloto y campaña formal

```powershell
java -jar dist/paqrap-experimentos.jar --config config/pilot.properties --output results/pilot-01
python scripts/verify_results.py results/pilot-01
python scripts/analyze.py results/pilot-01
python scripts/pilot_summary.py results/pilot-01
```

El piloto configurado tiene 12 instancias × 3 semillas × 4 presupuestos × 2 algoritmos = **288 corridas**.
Prueba 3, 5, 10 y 20 segundos; suma **45,6 minutos de presupuestos de búsqueda**, más inicio de JVM,
calentamiento, lectura y auditoría. No hay que introducir pedidos durante esos segundos:
las entradas quedan fijas antes de cronometrar.

Revisa cobertura, soluciones completas, costos sobre las mismas parejas y trazas. Elige y justifica
un presupuesto a partir del piloto. El comando siguiente muestra un EJEMPLO con 10 segundos,
no una recomendación automática ni un presupuesto ya aprobado:

```powershell
python scripts/freeze_formal.py --pilot results/pilot-01 --budget-ms 10000 --output config/formal-aprobado.properties --reason "Escribir aqui la justificacion concreta basada en el piloto completo."
java -jar dist/paqrap-experimentos.jar --config config/formal-aprobado.properties --output results/formal-01
python scripts/verify_results.py results/formal-01
python scripts/analyze.py results/formal-01
```

La plantilla formal contiene **40 instancias × 5 semillas × 2 algoritmos = 400 corridas**.
Está protegida con `campaign.frozen=false`: no se lanza accidentalmente antes de revisar el piloto.
`freeze_formal.py` conserva las instancias y semillas formales, copia los parámetros del piloto,
comprueba que el JAR y los datos del piloto no cambiaron y guarda hashes y justificación.
No selecciona un ganador, no demuestra que el presupuesto sea suficiente y no ejecuta la campaña.

El análisis estadístico/gráfico es opcional:

```powershell
python -m pip install -r scripts/analysis-requirements.txt
python scripts/analyze.py results/formal-01 --statistics --wilcoxon --plots
```

Debe definirse el análisis antes de inspeccionar los resultados formales. Las semillas son repeticiones
de una instancia, no observaciones independientes. Lee `docs/PROTOCOLO.md`.

## Interrumpir y reanudar

Una campaña nueva necesita una carpeta de salida nueva. Para continuar una campaña interrumpida:

```powershell
java -jar dist/paqrap-experimentos.jar --config config/pilot.properties --output results/pilot-01 --resume true
```

Las corridas terminadas, incluidas las fallidas, se conservan; no se repiten selectivamente.
Cambiar JAR, datos o configuración obliga a una campaña nueva. Mantén también el mismo equipo.
No ejecutes dos procesos apuntando a una misma carpeta de salida.

## Compilar cambios y probar

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\test.ps1
python scripts/test_java_sources.py
python -m unittest discover -s tests -v
```

En Linux/macOS, sustituye los dos comandos PowerShell por `bash scripts/build.sh` y
`bash scripts/test.sh`. Los comandos `java` y `python` son equivalentes.
Las pruebas de método Java adicionales se ejecutan con un adaptador offline propio, **no con
el motor JUnit**. Los archivos JUnit siguen disponibles para `mvn test` en un entorno con Maven.
Las pruebas Python usan copias temporales de la evidencia como fixtures y no la sobrescriben.

**Si editas Java, recompila el JAR antes de ejecutar.** Recompilar después de calibrar puede cambiar
el hash del binario: no mezcles versiones en una misma campaña.

## Contenido

- `dominio/`: pedidos, vehículos, inventario, bloqueos, rutas, descanso flexible, validador y control de búsqueda.
- `grasp/`, `sa/`: estrategias metaheurísticas sobre el mismo dominio.
- `experimentos/`: instancias, ejecutor, adaptadores, auditoría y exportación.
- `config/`, `data/`: campañas y datos; mantenimiento no se carga en el experimento.
- `scripts/`, `tests/`: compilación, pruebas, auditoría, calibración y análisis.
- `evidencia/flex1/`: ejecuciones de verificación de esta versión, no resultados formales.
- `docs/`: regla de descanso, protocolo, limitaciones y resultados verificados.
- `provenance/`: archivos de entrada y cambios respecto de la base utilizada.

Para actualizar tu rama, reemplaza la carpeta experimental anterior por esta carpeta completa,
no mezcles clases de ambas versiones. No se incluyen ni modifican archivos del resto del sistema.
