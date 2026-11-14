$ErrorActionPreference = "Stop"
Push-Location $PSScriptRoot
try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw "Maven no esta en PATH. Configure Maven y Java 21 antes de ejecutar este script."
    }
    Write-Host "[1/2] Pruebas focalizadas de coexistencia, aislamiento, colapso y carga mensual..."
    mvn clean test "-Dtest=ConcurrenciaYAislamientoTest,EscenarioApiTest,EventosYMonitoreoApiTest,PedidoApiTest,BloqueoApiTest,ReplanificacionFlujoApiTest,ParametrosFlotaReplanificacionApiTest"
    if ($LASTEXITCODE -ne 0) { throw "Fallaron las pruebas focalizadas. Revise target/surefire-reports." }

    Write-Host "[2/2] Suite completa..."
    mvn test
    if ($LASTEXITCODE -ne 0) { throw "Fallo la suite completa. Revise target/surefire-reports." }

    Write-Host "VALIDACION COMPLETADA. Puede iniciar el backend con mvn spring-boot:run."
}
finally {
    Pop-Location
}
