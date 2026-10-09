# PaqRap - Base de datos V2 reproducible

Este paquete permite levantar una base MySQL limpia para PaqRap sin depender de la BD local de otro integrante ni de ejecutar manualmente V1 + migración.

## Archivos

- `database/paqrap_schema_v2.sql`: crea directamente la estructura V2 final (15 tablas).
- `database/paqrap_seed_v2.sql`: carga únicamente datos maestros estables: 3 tipos de vehículo, 3 almacenes y 37 vehículos.
- `database/paqrap_smoke_test.sql`: comprobación rápida de la instalación.
- `docker-compose.db.yml`: levanta MySQL aislado en Docker.
- `.env.example`: variables de desarrollo local.
- `database/paqrap_migration_v1_to_v2.sql`: se conserva únicamente para una BD antigua que ya esté en V1; no se usa al crear una BD nueva con Docker.

## Qué NO carga el seed

El seed base no inventa datos operativos. Por eso deja vacíos pedidos, mantenimientos, bloqueos, averías, ejecuciones, resultados, planes, rutas, paradas e inventario transaccional. Esos datos deben llegar desde archivos oficiales, API/backend o casos de prueba.

## Levantar MySQL con Docker

Ubicar `docker-compose.db.yml` dentro de `paqrap-back/` y los SQL dentro de `paqrap-back/database/`.

Desde `paqrap-back/`:

```powershell
Copy-Item .env.example .env

docker compose -f docker-compose.db.yml up -d

docker compose -f docker-compose.db.yml ps
```

La BD queda disponible por defecto en:

- Host: `localhost`
- Puerto host: `3307`
- Base: `paqrap`
- Usuario: `paqrap`
- Contraseña local por defecto: `paqrap_dev`

El puerto interno del contenedor sigue siendo `3306`; se usa `3307` en el host para no chocar con un MySQL local que ya use `3306`.

## Verificar

Desde MySQL Workbench se puede crear una conexión a `127.0.0.1:3307` con el usuario `paqrap`.

O por Docker:

```powershell
docker exec -it paqrap-mysql mysql -upaqrap -ppaqrap_dev paqrap
```

Luego ejecutar `database/paqrap_smoke_test.sql`.

Esperado:

- 15 tablas.
- 3 tipos de vehículo.
- 3 almacenes.
- 37 vehículos: 10 autos, 15 motocicletas y 12 bicicletas.
- tablas operativas inicialmente vacías.

## Conexión desde Spring Boot

Spring Boot puede usar estas variables:

```text
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/paqrap?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=paqrap
SPRING_DATASOURCE_PASSWORD=paqrap_dev
```

Jimena puede conectar JPA/repositories a esta BD sin depender del MySQL local de Cristhian. La integración JPA y los repositories pertenecen al backend; este paquete sólo deja preparada y reproducible la BD.

## Reiniciar desde cero

Los scripts de `/docker-entrypoint-initdb.d` se ejecutan automáticamente sólo cuando el volumen de MySQL está vacío.

Para destruir únicamente la BD local Docker y recrearla desde cero:

```powershell
docker compose -f docker-compose.db.yml down -v
docker compose -f docker-compose.db.yml up -d
```

**Advertencia:** `down -v` elimina los datos almacenados en el volumen local de Docker.

## V1 -> V2

`paqrap_migration_v1_to_v2.sql` sigue siendo útil si ya existe una BD V1 con datos. Para una instalación nueva no debe ejecutarse: se usa directamente `paqrap_schema_v2.sql` + `paqrap_seed_v2.sql`.
