# PaqRap — Laboratorio de experimentación GRASP vs. SA

Proyecto independiente para comparar **las implementaciones Java entregadas por el equipo**, con **un solo dominio operativo** y **un evaluador común**. No requiere Spring Boot, PostgreSQL, React ni una conexión a internet para ejecutar el JAR incluido.

**Alcance:** planificación por lotes a partir de un estado operativo conocido. No es todavía el simulador online de cinco días ni el escenario completo de colapso. Los resultados son planes calculados, no entregas observadas en una simulación.

> **Versión 2 (23-sep-2026).** GRASP corregido y optimizado (`GRASP-v2 2026-09-23`: entregas divididas, consolidación en el primer viaje, costo exacto por tramo, factibilidad alineada con el evaluador) y cálculo de caminos compartido ~30× más rápido con resultado idéntico. SA no se modificó. Nuevo perfil `escalabilidad`. Detalle y verificación en `docs/CAMBIOS_Y_PROCEDENCIA.md` y `docs/VERIFICACION.md`. No mezclar resultados de la versión 1 con los de la versión 2 (la columna `algorithm_version` los distingue).

> **Versión 3 (23-sep-2026).** SA conserva constructor, fórmula, vecindario y parámetros. Se corrigió `evaluatedNeighbors`, se añadió telemetría de costo inicial/iteraciones/temperatura final y se amplió la suite de regresión. La etiqueta SA es `SA-operational-v1.1 + shared-domain-v2 (metricas; 2026-09-23)`. La campaña smoke conservó exactamente planes y costos de v2; ver `docs/VERIFICACION.md`. Piloto y formal siguen pendientes.

## 1. Empezar en Windows / VS Code

Descomprime el ZIP. Abre la carpeta `paqrap-experimentacion` —la que contiene `pom.xml`, `config`, `data` y `dist`— y abre una terminal PowerShell allí.

Comprueba Java:

```powershell
java -version
```

El proyecto se compiló para **Java 21**. Para comparar corridas utiliza la misma versión exacta del JDK en todas. No mezcles resultados de distintas máquinas o versiones como si fueran un único experimento.

Primero comprueba el proyecto:

```powershell
java -jar dist/paqrap-experimentos.jar --self-test
```

Después ejecuta una prueba pequeña:

```powershell
java -jar dist/paqrap-experimentos.jar --config config/smoke.properties
```

Se creará una carpeta nueva dentro de `results/`. Al terminar, la terminal mostrará su ruta. Abre **`report.html`** de esa carpeta con doble clic. También puedes abrirlo durante la ejecución y actualizarlo: el informe se regenera después de cada corrida.

**La versión de `dist/` corresponde al código fuente entregado.** Si cambias un `.java`, vuelve a compilar antes de ejecutar; editar solamente `.properties` o los CSV no necesita recompilación.

## 2. Qué hay dentro

```text
paqrap-experimentacion/
├── dominio/          # Única copia de domain/route: vehículos, red, inventario, evaluador
├── grasp/            # GraspPlanificador y ResultadoPlanificacion originales adaptados
├── sa/               # Familia Operational* de SA, sin otra copia del dominio
├── experimentos/     # Adaptadores, instancias, presupuestos, corridas, auditoría e informes
├── config/           # Perfiles y tablas de instancias editables
├── data/             # Archivos originales de pedidos, bloqueos y mantenimiento
├── scripts/          # Compilación, ejecución y análisis
├── dist/             # JAR ejecutable compilado, sin dependencias externas de ejecución
├── docs/             # Protocolo, cambios, limitaciones y diccionario de resultados
├── evidencia/        # Pruebas realmente realizadas; NO es la experimentación definitiva
├── provenance/       # Origen de fuentes, huellas SHA-256 y diferencias introducidas
└── pom.xml           # Proyecto padre Maven de cuatro módulos
```

No se reemplazaron GRASP y SA por algoritmos nuevos. Se conservó su lógica de búsqueda y se añadieron adaptadores y controles para medirlos de forma comparable. SA conserva su constructor inicial propio; **no arranca con una solución de GRASP**.

## 3. Tres etapas de trabajo

| Perfil | Instancias | Semillas por instancia | Algoritmos | Corridas | Presupuesto máximo por corrida |
|---|---:|---:|---:|---:|---:|
| `smoke` | 4 | 2 | 2 | 16 | 1.5 s |
| `pilot` | 12 | 3 | 2 | 72 | 3 s |
| `formal` | 40 | 5 | 2 | 400 | 5 s |
| `escalabilidad` | 8 | 3 | 2 | 96 | 5 s y 20 s |

`escalabilidad` (versión 2) estudia volumen: 24, 48, 96 y 144 pedidos sintéticos y ventanas reales de 1, 2, 4 y 8 h (21–24 de septiembre). En ventanas reales largas se excluyen pedidos ya vencidos al planificar y la auditoría señala los que ningún plan puede cubrir (`orders_provably_unservable`); ver `docs/PROTOCOLO.md`.

```powershell
java -jar dist/paqrap-experimentos.jar --config config/escalabilidad.properties
```

Los valores son un **diseño experimental inicial propuesto**, no requisitos del docente ni parámetros óptimos demostrados. Antes de la campaña formal, usa el piloto para comprobar tiempos y congelar parámetros. Los datos reales del piloto corresponden al 9–12 de septiembre; los formales al 13–20, evitando usar los mismos lotes reales para ajustar y evaluar.

### Piloto

```powershell
java -jar dist/paqrap-experimentos.jar --config config/pilot.properties
```

### Experimento formal

```powershell
java -jar dist/paqrap-experimentos.jar --config config/formal.properties --output results/formal-equipo
```

400 × 5 s = 2000 s de presupuesto máximo **de búsqueda**. El tiempo total de la campaña será mayor por arranque de JVM, lectura, calentamiento y auditorías; algunas búsquedas también pueden terminar antes. No se promete un tiempo total fijo.

No abras dos experimentos a la vez. Evita otros trabajos intensivos en el equipo durante las mediciones.

### Reanudar una campaña interrumpida

```powershell
java -jar dist/paqrap-experimentos.jar --config config/formal.properties --output results/formal-equipo --resume true
```

Debe ser la misma configuración, tabla de instancias, datos y compilación. Se conservan las corridas ya terminadas, **incluidos fallos y timeouts**; no se repiten selectivamente hasta que salgan bien. Para cambiar condiciones crea otra carpeta de salida. Los identificadores, semillas y presupuesto permiten reconstruir las parejas.

## 4. Cómo modificar el experimento

Edita el archivo `.properties` del perfil. Por ejemplo:

```properties
seeds=42,73,101,211,997
budgets.ms=5000
grasp.alpha=0.3
sa.temperature=1000
sa.minimumTemperature=1
sa.cooling=0.95
sa.iterationsPerTemperature=50
worker.heap.mb=512
worker.processors=2
```

Para estudiar más de un presupuesto puedes escribir `budgets.ms=1000,3000,5000`. Se forman parejas separadas por presupuesto y aumenta proporcionalmente el número de corridas. No mezcles todos los presupuestos en una única media.

La lista de escenarios está en `config/formal.csv` (o el CSV del perfil elegido). Las familias son `NORMAL`, `BLOCKED`, `REDUCED`, `SPLIT` y `REAL`. Las primeras cuatro son **sintéticas y controladas**; `REAL` usa datos de los archivos entregados por el equipo. No se presentan los sintéticos como datos del docente.

**Importante:** el modo `FIXED` y `config/deterministic.properties` sirven para comprobar repetibilidad con límites de iteraciones. No son la comparación de rendimiento: una iteración de GRASP no equivale al mismo trabajo que una de SA.

## 5. Qué se considera mejor

Se separan dos preguntas:

1. **Efectividad:** ¿en cuántas corridas el algoritmo encontró un plan que cubre todos los pedidos y cumple el evaluador común?
2. **Calidad y eficiencia:** entre parejas donde ambos lo consiguen, ¿cuál obtiene menor costo? ¿Cuánto tarda en encontrar su primer plan completo? ¿Cuántas evaluaciones y consultas de caminos utiliza?

El costo de un plan parcial **no compite** contra el costo de un plan completo. No se introdujeron penalizaciones monetarias artificiales. Los fallos no se eliminan del denominador de éxito.

### Ejemplo conceptual

- GRASP atiende 7/10 pedidos por S/300.
- SA atiende 10/10 por S/500.

No se concluye que GRASP sea mejor por costar menos: en esa pareja GRASP no resolvió el problema completo. Su costo comparable queda vacío; su cobertura y su fallo se conservan.

## 6. Archivos de salida

| Archivo | Contenido |
|---|---|
| `report.html` | Informe local legible en navegador, sin servidor ni instalación adicional |
| `runs.csv` | Una fila por corrida, incluyendo errores, límites y planes parciales |
| `paired.csv` | Parejas GRASP/SA; diferencia de costo solo si ambos son completos y factibles |
| `metadata.json` | JDK, sistema, parámetros, memoria configurada, huellas de configuración/JAR |
| `instances/*.json` | Datos exactos de cada instancia: pedidos, vehículos, stock, bloqueos y fechas |
| `jobs/*.plan.json` | Plan, rutas, cronogramas, cargas, recorridos, incumplimientos de la auditoría |
| `jobs/*.trace.csv` | Mejoras encontradas durante la búsqueda y tiempo transcurrido |
| `jobs/*.log` | Mensajes de la corrida; consultar ante un error |

Las fechas de los JSON se expresan como instantes (`Z`/UTC). La construcción del escenario utiliza la zona `America/Lima`; no confundas UTC con la hora local de inicio.

## 7. Análisis opcional

El resumen básico solo necesita Python 3.10 o superior y su biblioteca estándar:

```powershell
python scripts/analyze.py results/formal-equipo
```

Crea `analysis/summary.csv`, `analysis/paired_runs.csv`, `analysis/per_instance.csv` y `analysis/analysis.json`.

Para gráficos y análisis estadístico adicional, utiliza un entorno Python separado (la combinación fijada en el archivo de dependencias se probó con Python 3.13):

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r scripts/analysis-requirements.txt
.venv\Scripts\python.exe scripts/analyze.py results/formal-equipo --statistics --wilcoxon --plots
```

El script no convierte cinco semillas de una misma instancia en cinco instancias independientes. Calcula primero resultados por instancia. La comparación de costo es condicional a instancias donde ambos tienen éxito en todas sus repeticiones. Consulta `docs/PROTOCOLO.md` para los supuestos y límites de esa inferencia.

No copies los resultados de `evidencia/smoke-verificado/` como conclusión final del curso: son una prueba pequeña del laboratorio.

## 8. Compilar después de modificar código

### Sin Maven, con JDK 21

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
```

Linux/macOS:

```bash
bash scripts/build.sh
```

### Con Maven

```powershell
mvn clean verify
java -jar experimentos/target/paqrap-experimentos.jar --config config/smoke.properties
```

El POM está preparado para JDK 21 y JUnit 5.11.4. La primera construcción Maven necesita descargar plugins/dependencias. **Usa siempre `clean`**: sin él, el empaquetado con `maven-shade-plugin` puede reutilizar un JAR anterior y mezclar clases viejas con nuevas. La versión 2 se verificó en Windows 11 con `mvn clean verify` (22 pruebas JUnit) y `scripts/build.sh` desde Git Bash; `scripts/build.ps1` no se ejecutó.

Puedes importar el `pom.xml` raíz en IntelliJ o abrir la carpeta raíz en VS Code. No necesitas copiar el proyecto dentro de tu frontend ni inicializar una aplicación Spring.

## 9. Limitaciones que importan antes de concluir

- Desde la versión 2, GRASP sí divide un pedido mayor que la capacidad de cualquier vehículo (P&R 13); la versión 1 no lo hacía. Es un cambio algorítmico explícito y versionado.
- SA depende de un constructor inicial que puede fallar aunque exista otra solución. Ese caso se registra como `NO_INITIAL_PLAN`. Si un solo pedido es no atendible, SA falla completo; GRASP devuelve el mejor plan parcial.
- Con la red optimizada, el esquema de enfriamiento de SA termina en 1–2 s, antes de presupuestos de 5 s o más; GRASP usa todo el presupuesto. Se compara a igual tiempo **máximo**; revisar `elapsed_ms` y calibrar en el piloto si se quiere igual tiempo **usado**.
- Los operadores de SA pueden generar vecinos inválidos por carga/abastecimiento; el evaluador común los rechaza. No se cambió esa estrategia de vecindad.
- El dominio heredado tiene decisiones que requieren validación del equipo (refrigerio y regla de 80 km entre paradas). Comparabilidad entre algoritmos no significa conformidad completa con todos los requisitos de PaqRap.
- No encontrar una solución en un presupuesto de tiempo **no demuestra imposibilidad ni colapso real**.

Lee `docs/LIMITACIONES.md` antes de redactar conclusiones. El documento de referencia se usó como guía de organización, no para copiar reglas de equipaje aéreo, penalizaciones ni resultados AG/BT.

## 10. Reutilización posterior en el backend

`dominio`, `grasp` y `sa` son módulos independientes del experimento. El futuro servicio de aplicación de Spring Boot puede invocarlos sin depender del ejecutor de CSV. Los adaptadores experimentales ilustran cómo normalizar sus entradas y salidas sin duplicar el dominio.

**Estado verificado (versión 2, 23-sep-2026, Windows 11):** `mvn clean verify` con 22 pruebas JUnit sin fallos (incluye la prueba diferencial de exactitud de la red de caminos); `--self-test` con 22 comprobaciones; smoke 16/16 `OK`; repetibilidad FIXED en dos ejecuciones; SA con planes idénticos bit a bit a la versión 1; perfil `escalabilidad` completo (96 corridas). Piloto y campaña formal están configurados y sus instancias cargan, pero **no** se ejecutaron: son la experimentación que debe correr el equipo. Resultados y cifras en `docs/VERIFICACION.md`; evidencia en `evidencia/v2/`.

**Estado verificado (versión 3, 23-sep-2026):** 32 pruebas JUnit sin fallos, `--self-test` 22/22, smoke 16/16 `OK` y dos ejecuciones `FIXED` reproducibles. El JAR de `dist/` se compiló con `scripts/build.ps1` y Java 22 con `--release 21`. El perfil `escalabilidad` no se volvió a ejecutar en esta versión; piloto y formal siguen sin ejecutarse. Evidencia y limitaciones en `docs/VERIFICACION.md`.
