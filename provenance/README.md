# Trazabilidad de los fuentes

## Versión 2 (23-sep-2026)

- `SHA256SUMS` (raíz de esta carpeta) describe el árbol **actual** de la versión 2, con el mismo alcance que en la versión 1 (todo salvo `evidencia/`, `results/` y salidas de compilación).
- `v1/SHA256SUMS`: huellas del entregable recibido (versión 1), conservadas sin cambios.
- `v2/archivos_v2.json`: cada archivo agregado, modificado o eliminado respecto de la versión 1, con su SHA-256 antes y después.
- `v2/cambios_v2.patch`: diferencia textual exacta de fuentes, configuración, scripts y documentos respecto del ZIP recibido (excluye `evidencia/`, `dist/` y esta carpeta).

El resto de esta carpeta (`archives.sha256`, `origen_archivos.json`, `cambios.patch`) documenta la versión 1 respecto de los ZIP originales de GRASP y SA y no se modificó.

## Versión 1

`archives.sha256` identifica los dos ZIP de entrada. `origen_archivos.json` conserva el origen y SHA-256 de los fuentes copiados, sus huellas entregadas y el estado unchanged/modified/added. `cambios.patch` muestra las modificaciones sobre los archivos originales, sin confundirlas con clases nuevas.

Los README originales se conservan como antecedentes. Sus instrucciones, estructura y resultados históricos NO sustituyen el README del laboratorio ni la evidencia ejecutada en este entregable.

El JAR compilado contiene exclusivamente clases de este proyecto. No se distribuyen bibliotecas binarias de terceros ni las carpetas `.m2` que aparecían en un ZIP de entrada.
