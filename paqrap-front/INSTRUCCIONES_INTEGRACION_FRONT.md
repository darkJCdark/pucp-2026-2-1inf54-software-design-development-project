# PaqRap Frontend listo para el backend actual

## Qué incluye

- React Konva para el mapa operativo.
- Rutas y paradas del último plan de Simulated Annealing.
- Zoom, desplazamiento, filtros y tooltips.
- Métricas reales de monitoreo: costo, distancia, entregas, paquetes y vehículos en ruta.
- Replanificaciones y detalle del plan.
- Pedidos filtrados por el periodo de la ejecución, con tabla, filtros e historial.
- Registro manual de pedidos en Día a Día.
- Registro manual de averías en Día a Día y 5D.
- Carga de ventas y bloqueos para 5D/Colapso.
- Historial de ejecuciones.
- Programación y cancelación de mantenimientos.
- Velocidades de flota leídas y actualizadas contra el backend.

## Instalación en Windows

Desde `paqrap-front`:

```powershell
.\PREPARAR_FRONT.ps1
```

El script elimina el `package-lock.json` anterior, instala las dependencias —incluyendo Konva—, genera un lock nuevo y ejecuta el build de producción.

Luego:

```powershell
npm run dev
```

Antes de subir cambios:

```powershell
npm run build
git add package.json package-lock.json src Dockerfile PREPARAR_FRONT.ps1 preparar-front.sh
git commit -m "feat: integra front operativo con Konva"
```

## Instalación en Linux

```bash
chmod +x preparar-front.sh
./preparar-front.sh
```

## Docker

El Dockerfile usa temporalmente `npm install` para que la primera reconstrucción incorpore las nuevas dependencias aunque exista un lock viejo. Después de ejecutar el script y versionar el lock nuevo, se puede volver a `npm ci`.

Desde la raíz de PaqRap:

```bash
docker compose up -d --build
docker compose ps
```

## Pruebas mínimas

1. Abrir `/simulacion`, seleccionar 5D y cargar `ventas.AAAAMM.txt`.
2. Cargar opcionalmente `bloqueo.AAMM.txt` del mismo periodo.
3. Iniciar y confirmar que el reloj avanza.
4. Ver pedidos, vehículos, almacenes y rutas en el mapa Konva.
5. Confirmar que costo, distancia, entregas y vehículos en ruta cambian.
6. Pausar, reanudar y detener.
7. Abrir `/ejecuciones` y recuperar una ejecución.
8. Abrir `/pedidos` y revisar filtros/detalle/historial.
9. Abrir `/mantenimientos`, programar y cancelar una fecha.

## Límites del backend que el frontend no puede resolver

- El endpoint de planificación devuelve paradas, pero no la geometría exacta de cada camino vial. Por eso las rutas se dibujan como recorridos ortogonales indicativos entre paradas.
- El último plan y algunas métricas todavía pueden perderse al reiniciar Spring si el backend no termina de persistir `plans`, `routes`, `route_stops` y `scenario_results`.
- El backend no ofrece CRUD para cambiar la cantidad de vehículos; por eso las cantidades aparecen informativas y solo las velocidades son editables.
