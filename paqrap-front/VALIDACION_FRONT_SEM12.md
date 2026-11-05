# Validación del frontend — semana 12

## Verificado en este entorno

- 80 archivos TypeScript/TSX analizados sin errores sintácticos.
- Comprobación semántica estática con TypeScript y declaraciones temporales de dependencias externas: sin errores internos detectados.
- Imports locales revisados: 0 rutas faltantes.
- Contratos del frontend contrastados estáticamente con controladores/DTO del backend actual.
- No se modificaron archivos de backend.

## No ejecutado en este entorno

No fue posible completar `npm ci` porque el entorno no resuelve `registry.npmjs.org`. Por ello no se pudo ejecutar aquí el build Vite ni ESLint con las dependencias reales.

En el equipo local se debe ejecutar:

```bash
npm ci
npm run build
npm run lint
```

## Prueba manual mínima

### Configuración

1. Seleccionar 5D en un periodo ya cargado y confirmar que permite iniciar sin archivo.
2. Seleccionar un mes no cargado y confirmar mensaje con `ventas.AAAAMM.txt`.
3. Abrir Configuración avanzada y validar parámetros opcionales.

### Ejecución

1. Verificar los cuatro tiempos.
2. Abrir panel de operación y probar las tres listas.
3. Seleccionar desde lista y desde mapa en ambos sentidos.
4. Probar filtros de carga y stock.
5. Cargar bloqueos y validar vigencia y replanificación.
6. Registrar avería y comprobar posición/motivo/hora de término.
7. Cambiar velocidad y confirmar nueva replanificación.
8. Finalizar y abrir resultado/CSV.

### Día a Día

1. Iniciar sin archivo de ventas.
2. Registrar pedido.
3. Registrar avería.
4. Pausar, reanudar y detener.
