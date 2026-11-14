import { Card } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { useConfiguracion } from "@/context/ConfiguracionContext";

import type { Escenario } from "./configuracion.types";
import { periodoLegible } from "./periodos";

interface ResumenConfiguracionProps {
  mesesRequeridos: string[];
  mesesFaltantes: string[];
}

export function ResumenConfiguracion({
  mesesRequeridos,
  mesesFaltantes,
}: ResumenConfiguracionProps) {
  const { configuracion, archivoPedidos, archivoBloqueos } = useConfiguracion();

  const fuentePedidos =
    configuracion.escenario === "DIA_A_DIA"
      ? "Registro en línea"
      : archivoPedidos?.name ??
        (mesesFaltantes.length === 0 && mesesRequeridos.length > 0
          ? `Base de datos · ${mesesRequeridos.map(periodoLegible).join(", ")}`
          : mesesFaltantes.length > 0
            ? `Falta ${mesesFaltantes.map(periodoLegible).join(", ")}`
            : "Selecciona una fecha");

  return (
    <Card className="h-fit border-slate-800 bg-[#151b23] p-4">
      <h2 className="mb-5 font-semibold">Resumen de configuración</h2>

      <Fila label="Escenario" value={nombreEscenario(configuracion.escenario)} />
      <Fila label="Pedidos" value={fuentePedidos} />

      {configuracion.escenario !== "DIA_A_DIA" && (
        <>
          <Fila label="Bloqueos" value={archivoBloqueos?.name ?? "Sin archivo"} />
          <Fila label="Inicio" value={configuracion.inicioSimulado || "—"} />
        </>
      )}

      {configuracion.usarConfiguracionSa && (
        <Fila
          label="Configuración avanzada"
          value={`Semilla ${configuracion.saSemilla || "aleatoria"} · T₀ ${configuracion.saTemperaturaInicial} · α ${configuracion.saFactorEnfriamiento}`}
        />
      )}

      <Separator className="my-4 bg-slate-800" />

      <p className="mb-2 text-xs uppercase text-slate-500">Flota</p>
      <Fila
        label="Autos"
        value={`${configuracion.autosCantidad} ud · ${configuracion.autosVelocidad} km/h`}
      />
      <Fila
        label="Motos"
        value={`${configuracion.motosCantidad} ud · ${configuracion.motosVelocidad} km/h`}
      />
      <Fila
        label="Bicicletas"
        value={`${configuracion.bicicletasCantidad} ud · ${configuracion.bicicletasVelocidad} km/h`}
      />

      <Separator className="my-4 bg-slate-800" />
      <p className="mb-2 text-xs uppercase text-slate-500">Semáforo de inventario</p>
      <Fila label="Llenado vacío" value="0%" />
      <Fila label="Llenado verde" value={`> 0% y ≤ ${configuracion.almacenVerdeHasta}%`} />
      <Fila label="Llenado ámbar" value={`> ${configuracion.almacenVerdeHasta}% y ≤ ${configuracion.almacenAmbarHasta}%`} />
      <Fila label="Llenado rojo" value={`> ${configuracion.almacenAmbarHasta}%`} />

      <Separator className="my-4 bg-slate-800" />
      <p className="mb-2 text-xs uppercase text-slate-500">Semáforo de carga</p>
      <Fila label="Carga vacía" value="0%" />
      <Fila label="Carga verde" value={`> 0% y ≤ ${configuracion.vehiculoVerdeHasta}%`} />
      <Fila label="Carga ámbar" value={`> ${configuracion.vehiculoVerdeHasta}% y ≤ ${configuracion.vehiculoAmbarHasta}%`} />
      <Fila label="Carga roja" value={`> ${configuracion.vehiculoAmbarHasta}%`} />
    </Card>
  );
}

function nombreEscenario(escenario: Escenario) {
  switch (escenario) {
    case "DIA_A_DIA":
      return "Día a Día";
    case "SIMULACION_5D":
      return "Simulación 5D";
    case "COLAPSO":
      return "Colapso";
  }
}

function Fila({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-slate-800 py-2 text-xs last:border-0">
      <span className="uppercase text-slate-500">{label}</span>
      <span className="max-w-[62%] text-right font-medium text-slate-200">{value}</span>
    </div>
  );
}
