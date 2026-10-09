$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "========================================"
Write-Host " PaqRap - Backend local"
Write-Host "========================================"
Write-Host ""

# -------------------------------
# Maven
# -------------------------------

$env:MAVEN_HOME = "C:\Tools\apache-maven-3.10.0"
$env:Path = "$env:MAVEN_HOME\bin;$env:Path"

Write-Host "[1/4] Maven"

mvn -version

# -------------------------------
# Base de datos
# -------------------------------

Write-Host ""
Write-Host "[2/4] Levantando MySQL Docker..."

docker compose `
  -f docker-compose.db.yml `
  up -d

# -------------------------------
# Esperar healthcheck
# -------------------------------

Write-Host ""
Write-Host "[3/4] Esperando a MySQL..."

$maxIntentos = 40
$intento = 0

do {

    Start-Sleep -Seconds 2

    $estado = docker inspect `
      --format "{{.State.Health.Status}}" `
      paqrap-mysql `
      2>$null

    $intento++

    Write-Host "MySQL: $estado"

    if ($intento -ge $maxIntentos) {
        throw "MySQL no llegó a estado healthy."
    }

} while ($estado -ne "healthy")

Write-Host ""
Write-Host "MySQL listo."

# -------------------------------
# Variables Spring
# -------------------------------

$env:PAQRAP_DB_URL = "jdbc:mysql://127.0.0.1:3307/paqrap?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true&useSSL=false&allowPublicKeyRetrieval=true"

$env:PAQRAP_DB_USER = "paqrap"

$env:PAQRAP_DB_PASSWORD = "paqrap_dev"

$env:PAQRAP_PORT = "8080"

# Motor de escenarios

$env:PAQRAP_ESCENARIOS_TICK = "1s"

$env:PAQRAP_FACTOR_CINCO_DIAS = "160"

$env:PAQRAP_FACTOR_COLAPSO = "1440"

$env:PAQRAP_INTERVALO_PLANIFICACION = "1h"

# SA

$env:PAQRAP_PLAN_MAX_PEDIDOS = "100"

$env:PAQRAP_PLAN_PRESUPUESTO_MS = "1500"

# -------------------------------
# Spring Boot
# -------------------------------

Write-Host ""
Write-Host "[4/4] Iniciando Spring Boot..."
Write-Host ""

Write-Host "BD       : localhost:3307"
Write-Host "Backend  : localhost:8080"
Write-Host "5D       : x160"
Write-Host "Colapso  : x1440"
Write-Host "Plan SA  : cada 1h simulada"
Write-Host ""

mvn spring-boot:run