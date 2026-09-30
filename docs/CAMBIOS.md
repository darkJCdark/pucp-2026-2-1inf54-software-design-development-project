# Cambios de código respecto de experimentacion_v3

## Descanso y dominio

`ShiftSchedule` define ventanas y márgenes, no horas fijas de refrigerio. `FlexibleMealClock`
mantiene el estado de una alternativa, `RouteScheduler` explora horarios y `ScheduledMealBreak`
representa cada pausa. `MealBreakAudit` valida sin depender de las decisiones del programador.
Se añadieron `serviceStartedAt`, `returnedAt`, `duty_completed_at` y detalles de descanso.

El evaluador común conserva comprobaciones de cargas, stock cronológico, bloqueos y demanda
original. Su caché de horarios equivalentes reutiliza programación entre vehículos idénticos,
pero no reutiliza resultados de validación de capacidad, disponibilidad o inventario. Se invalida
cuando cambian snapshot o bloqueos y está acotada para evitar crecimiento sin límite.

## Algoritmos

Se conserva la mecánica corregida de GRASP/SA de v3: inserciones factibles compartidas, reparación
de cargas después de cambios, soluciones parciales auditables, mejor plan al agotar presupuesto,
SA con inicialización dentro del tiempo y recalentamiento en TIME.
Las aperturas de rutas de vehículos ociosos en Central se hacen con el mismo constructor de
inicio de escenario para que ambos algoritmos tengan la misma convención de descanso/carga inicial.

## Experimentos

Se rechaza la clave de descanso fijo y configuraciones que omitan/acorten la hora. Se incluyen
bloqueos también en las familias sintéticas NORMAL, SPLIT y REDUCED. Se mantuvo la exclusión de
mantenimiento/averías y ausencia de límite base de 80 km. Las configuraciones incluyen la política
flexible y un ancho común de alternativas. Piloto con cuatro presupuestos; formal protegida hasta
congelación. `--validate-only true` inspecciona entradas sin ejecutar búsqueda.

El JSON, CSV y HTML incluyen descansos; la auditoría final es común. Se añade verificación
independiente de exportaciones, resumen comparable del piloto y congelación con justificación/hashes.
El análisis ya no calcula razones de costos brutos para planes incompletos bajo el subconjunto
`unruled_out`; esos campos quedan vacíos por compatibilidad. Se añaden controles de datos mezclados.

Se corrigieron pruebas Java heredadas que llamaban clases/ayudantes privados eliminados y
expectativas del horario fijo. Se agregan pruebas offline, casos aleatorios y pruebas de herramientas.

## Procedencia

Se revisó el módulo de la última rama recibida y se tomó como base el paquete corregido
`PaqRap_experimentacion_v3.zip` previamente compartido, para no reintroducir reglas heredadas.
No se hizo una unión ciega por nombre de rama ni se tomaron campañas antiguas como nueva evidencia.
`provenance/entradas.sha256.json` identifica ambos archivos recibidos; el patch permite revisar
los cambios sobre la base v3. Esta entrega contiene exclusivamente la carpeta experimental.

## Actualizar la rama

Guardar los resultados propios fuera de la carpeta que se reemplaza. En la rama correcta,
reemplazar la carpeta experimental completa por esta versión; no copiar encima dejando clases
viejas. Revisar `git status`, compilar/probar y registrar un commit con fuente, configuraciones,
pruebas y documentación. El resto de PaqRap no está incluido ni se modificó.
