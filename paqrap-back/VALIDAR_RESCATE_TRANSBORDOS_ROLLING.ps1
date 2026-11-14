$ErrorActionPreference = "Stop"

Write-Host "== PaqRap: validación de rescate, transbordos y rolling horizon ==" -ForegroundColor Cyan

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven no está disponible en PATH. Instala Maven y vuelve a ejecutar este script."
}

$mvnVersion = mvn -version
Write-Host $mvnVersion

$focusedTests = @(
    "TransferRouteSchedulerTest",
    "RescatePreventivoPedidosServiceTest",
    "RecuperacionTransbordoFallidoServiceTest",
    "EstadoVehiculoRutaServiceTest",
    "WarehouseSelectionPolicyTest",
    "WarehouseSelectionPolicyPressureTest",
    "SlaRescueInsertionSupportTest",
    "PlanResponseMapperTest",
    "OperationalAlnsMultiWarehouseTest",
    "InsercionRutasActivasMultiWarehouseTest",
    "LocalizadorRutaServiceTest",
    "ContinuacionRutaServiceTest"
) -join ","

Write-Host "`n[1/3] Pruebas focalizadas" -ForegroundColor Yellow
mvn clean test "-Dtest=$focusedTests"
if ($LASTEXITCODE -ne 0) { throw "Fallaron las pruebas focalizadas." }

Write-Host "`n[2/3] Suite Maven completa" -ForegroundColor Yellow
mvn test
if ($LASTEXITCODE -ne 0) { throw "Falló la suite Maven completa." }

Write-Host "`n[3/3] Empaquetado sin omitir pruebas" -ForegroundColor Yellow
mvn package
if ($LASTEXITCODE -ne 0) { throw "Falló el empaquetado Maven." }

Write-Host "`nVALIDACIÓN COMPLETADA" -ForegroundColor Green
Write-Host "Siguiente paso: aplicar la migración incremental en una copia de la BD y ejecutar el FIVE_DAY pareado."
