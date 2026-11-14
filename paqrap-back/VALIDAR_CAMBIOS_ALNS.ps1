$ErrorActionPreference = "Stop"

function Invoke-MavenTest {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [Parameter(Mandatory = $true)]
        [string] $Description
    )

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host $Description -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan

    & mvn @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "Falló: $Description"
    }
}

Invoke-MavenTest `
    -Description "1/3 - Pruebas nuevas: posición efectiva, almacenes y operadores ALNS" `
    -Arguments @(
        "clean",
        "test",
        "-Dtest=WarehouseSelectionPolicyTest,OperationalAlnsMultiWarehouseTest,EstadoVehiculoRutaServiceTest,InsercionRutasActivasMultiWarehouseTest"
    )

Invoke-MavenTest `
    -Description "2/3 - Regresión focalizada del planificador y la replanificación" `
    -Arguments @(
        "test",
        "-Dtest=OperationalAlnsPlannerTest,OperationalAlnsObjectivePlannerTest,ServiceFirstSeedRescueTest,VehicleSelectionPolicyTest,InsercionRutasActivasAlnsDesvioTest,UrgencyRemovalAlnsServiceTest,RedistribucionEntreVehiculosAlnsTest,LocalizadorRutaServiceTest,ContinuacionRutaServiceTest,ConfiguracionAlnsEscenarioIntegrationTest,ConfiguracionObjetivoPlanificacionTest"
    )

Invoke-MavenTest `
    -Description "3/3 - Suite completa" `
    -Arguments @(
        "test"
    )

Write-Host ""
Write-Host "VALIDACIÓN COMPLETA: BUILD SUCCESS" -ForegroundColor Green
