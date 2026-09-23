# Evidencia de funcionamiento, no conclusiones experimentales finales

Los archivos de esta carpeta son resultados efectivamente obtenidos al verificar el entregable. No son datos inventados ni resultados de AG/BT del informe de referencia. Las campañas piloto y formal siguen pendientes de ejecución por el equipo.

## Versión 2 (`GRASP-v2 2026-09-23`) — carpeta `v2/`

Obtenida en Windows 11, JDK 21, con el JAR de `dist/` de esta versión:

- `v2/smoke/`: perfil smoke (16 corridas).
- `v2/determinismo-a/` y `v2/determinismo-b/`: perfil FIXED dos veces; mismas huellas de plan.
- `v2/escalabilidad/`: perfil de escalabilidad completo (96 corridas, 24–144 pedidos sintéticos y ventanas reales de 1–8 h).
- `v2/self-test.log`, `v2/junit.log`, `v2/exactitud-red.txt`: autoverificación, pruebas JUnit y comparación de planes SA contra la versión 1.
- `v2/benchmark-versiones.txt`: GRASP antes/después con presupuestos fijos (ver `docs/VERIFICACION.md`).

## Versión 1 — carpetas en la raíz de `evidencia/`

`smoke-verificado/`, `determinismo-*` y los `.log` de la raíz corresponden a la versión 1 (antes de las correcciones de GRASP). Se conservan como antecedente; no se mezclan con la versión 2.

Empieza por `v2/smoke/report.html`. Los directorios `determinismo-*` contienen corridas FIXED con límites nativos de iteraciones, únicamente para probar repetibilidad, no para afirmar igualdad de esfuerzo computacional.

No mezcles estas corridas, de distinto propósito, en la tabla final del estudio. Ver `../docs/VERIFICACION.md`.
