import { useEffect, useState } from "react";
import { Check, Loader2, Pencil, X } from "lucide-react";

import type { TipoVehiculoBackend, TipoVehiculoResponseDto } from "@/api/flota.dto";
import type { PlanResponseDto, ReplanificacionResponseDto } from "@/api/planificacion.dto";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import type {
  AlmacenMapa,
  IndicadoresOperacion,
  TipoVehiculo,
  VehiculoMapa,
} from "./ejecucion.types";
import { PanelPlanificacion } from "./PanelPlanificacion";

interface PanelIndicadoresProps {
  indicadores: IndicadoresOperacion;
  almacenes: AlmacenMapa[];
  vehiculos: VehiculoMapa[];
  tiposFlota: TipoVehiculoResponseDto[];
  plan: PlanResponseDto | null;
  replanificacion: ReplanificacionResponseDto | null;
  errorPlan: string | null;
  permitirCambioVelocidad?: boolean;
  tipoActualizando?: TipoVehiculoBackend | null;
  onActualizarVelocidad?: (tipo: TipoVehiculoBackend, velocidad: number) => Promise<void>;
  onSeleccionarAlmacen?: (id: string) => void;
}

export function PanelIndicadores({
  indicadores,
  almacenes,
  vehiculos,
  tiposFlota,
  plan,
  replanificacion,
  errorPlan,
  permitirCambioVelocidad = false,
  tipoActualizando = null,
  onActualizarVelocidad,
  onSeleccionarAlmacen,
}: PanelIndicadoresProps) {
  const intermedios = almacenes.filter((almacen) => almacen.tipo === "INTERMEDIO");
  const totalPorTipo = (tipo: TipoVehiculo) =>
    vehiculos.filter((vehiculo) => vehiculo.tipo === tipo).length;
  const enRuta = (tipo: TipoVehiculo) =>
    vehiculos.filter(
      (vehiculo) => vehiculo.tipo === tipo && vehiculo.estado === "EN_RUTA",
    ).length;
  const detenidos = vehiculos.filter((vehiculo) => vehiculo.motivoDetencion !== null).length;

  return (
    <aside className="space-y-3 overflow-y-auto bg-[#11161d] p-3">
      <div className="grid grid-cols-2 gap-2">
        <Indicador
          titulo="Costo acumulado"
          valor={`S/ ${indicadores.costoAcumulado.toLocaleString("es-PE", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}`}
        />
        <Indicador
          titulo="Distancia recorrida"
          valor={`${indicadores.distanciaRecorrida.toFixed(2)} km`}
        />
        <Indicador titulo="Entregas completadas" valor={String(indicadores.entregasCompletadas)} />
        <Indicador titulo="Pedidos en riesgo" valor={String(indicadores.pedidosRiesgo)} />
      </div>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">Cumplimiento</h3>
        <Dato label="Entregados a tiempo" value={String(indicadores.entregasATiempo)} />
        <Dato label="Paquetes entregados" value={String(indicadores.paquetesEntregados)} />
        <Dato label="Unidades detenidas" value={String(detenidos)} />
      </Card>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">Utilización de flota</h3>
        <UsoFlota nombre="Autos" actual={enRuta("AUTO")} total={totalPorTipo("AUTO")} />
        <UsoFlota nombre="Motos" actual={enRuta("MOTO")} total={totalPorTipo("MOTO")} />
        <UsoFlota
          nombre="Bicicletas"
          actual={enRuta("BICICLETA")}
          total={totalPorTipo("BICICLETA")}
        />
      </Card>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <div className="mb-2 flex items-start justify-between gap-3">
          <div>
            <h3 className="font-semibold">Parámetros de flota</h3>
            <p className="mt-1 text-[10px] text-slate-500">
              El cambio se aplica al backend y dispara una replanificación del escenario activo.
            </p>
          </div>
        </div>

        {tiposFlota.map((tipo) => (
          <VelocidadEditable
            key={tipo.tipo}
            tipo={tipo}
            editable={permitirCambioVelocidad && Boolean(onActualizarVelocidad)}
            actualizando={tipoActualizando === tipo.tipo}
            onGuardar={(velocidad) => onActualizarVelocidad?.(tipo.tipo, velocidad) ?? Promise.resolve()}
          />
        ))}
      </Card>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">Almacenes intermedios</h3>

        {intermedios.map((almacen) => {
          const stock = almacen.stock ?? 0;
          const capacidad = almacen.capacidad ?? 1000;
          const porcentajeDisponible = capacidad > 0 ? (stock / capacidad) * 100 : 0;

          return (
            <button
              type="button"
              key={almacen.id}
              onClick={() => onSeleccionarAlmacen?.(almacen.id)}
              className="mb-5 block w-full rounded-md text-left transition hover:bg-slate-800/40 last:mb-0"
            >
              <div className="mb-2 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">Almacén {almacen.nombre}</p>
                  <p className="text-xs text-slate-500">{almacen.id}</p>
                </div>
                <span
                  className={
                    porcentajeDisponible > 66
                      ? "rounded-full bg-green-500 px-2 py-1 text-[10px] text-black"
                      : porcentajeDisponible > 33
                        ? "rounded-full bg-amber-500 px-2 py-1 text-[10px] text-black"
                        : "rounded-full bg-red-500 px-2 py-1 text-[10px] text-white"
                  }
                >
                  {porcentajeDisponible > 66 ? "Verde" : porcentajeDisponible > 33 ? "Ámbar" : "Rojo"}
                </span>
              </div>

              <Progress value={porcentajeDisponible} className="h-2" />
              <div className="mt-1 flex justify-between text-[11px] text-slate-500">
                <span>{stock} disponibles</span>
                <span>de {capacidad} uds.</span>
              </div>
            </button>
          );
        })}
      </Card>

      <PanelPlanificacion plan={plan} replanificacion={replanificacion} error={errorPlan} />
    </aside>
  );
}

function VelocidadEditable({
  tipo,
  editable,
  actualizando,
  onGuardar,
}: {
  tipo: TipoVehiculoResponseDto;
  editable: boolean;
  actualizando: boolean;
  onGuardar: (velocidad: number) => Promise<void>;
}) {
  const [editando, setEditando] = useState(false);
  const [velocidad, setVelocidad] = useState(String(tipo.velocidadKmh));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!editando) setVelocidad(String(tipo.velocidadKmh));
  }, [editando, tipo.velocidadKmh]);

  async function guardar() {
    const valor = Number(velocidad);
    if (!Number.isFinite(valor) || valor <= 0 || valor > 200) {
      setError("Ingresa una velocidad entre 0 y 200 km/h.");
      return;
    }

    setError(null);
    try {
      await onGuardar(valor);
      setEditando(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No fue posible actualizar la velocidad.");
    }
  }

  return (
    <div className="border-b border-slate-800 py-3 last:border-0">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm text-slate-300">{nombreTipo(tipo.tipo)}</p>
          <p className="text-[10px] text-slate-500">
            Cap. {tipo.capacidadPaquetes} · S/ {tipo.costoPorKm}/km
          </p>
        </div>

        {!editando ? (
          <div className="flex items-center gap-2">
            <span className="font-medium text-white">{tipo.velocidadKmh} km/h</span>
            {editable && (
              <button
                type="button"
                onClick={() => setEditando(true)}
                className="rounded p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white"
                aria-label={`Editar velocidad de ${nombreTipo(tipo.tipo)}`}
              >
                <Pencil className="size-3.5" />
              </button>
            )}
          </div>
        ) : (
          <div className="flex items-center gap-1">
            <input
              type="number"
              min={1}
              max={200}
              step={0.5}
              value={velocidad}
              onChange={(event) => setVelocidad(event.target.value)}
              className="w-20 rounded border border-slate-700 bg-slate-900 px-2 py-1 text-right text-xs outline-none focus:border-orange-500"
              aria-label="Nueva velocidad"
            />
            <span className="text-[10px] text-slate-500">km/h</span>
            <button
              type="button"
              onClick={() => void guardar()}
              disabled={actualizando}
              className="rounded p-1.5 text-green-400 hover:bg-green-950 disabled:opacity-50"
            >
              {actualizando ? <Loader2 className="size-3.5 animate-spin" /> : <Check className="size-3.5" />}
            </button>
            <button
              type="button"
              onClick={() => {
                setEditando(false);
                setError(null);
              }}
              disabled={actualizando}
              className="rounded p-1.5 text-slate-400 hover:bg-slate-800 disabled:opacity-50"
            >
              <X className="size-3.5" />
            </button>
          </div>
        )}
      </div>
      {error && <p className="mt-1 text-[10px] text-red-400">{error}</p>}
    </div>
  );
}

function Indicador({ titulo, valor }: { titulo: string; valor: string }) {
  return (
    <Card className="border-slate-800 bg-[#151b23] p-3">
      <p className="text-[10px] uppercase text-slate-500">{titulo}</p>
      <p className="mt-2 text-xl font-semibold text-white">{valor}</p>
    </Card>
  );
}

function UsoFlota({ nombre, actual, total }: { nombre: string; actual: number; total: number }) {
  const porcentaje = total > 0 ? (actual / total) * 100 : 0;
  return (
    <div className="mb-3">
      <div className="mb-1 flex justify-between text-xs">
        <span className="text-slate-400">{nombre}</span>
        <span className="text-slate-500">{actual}/{total} en ruta</span>
      </div>
      <Progress value={porcentaje} className="h-2" />
    </div>
  );
}

function Dato({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-slate-800 py-2 text-sm last:border-0">
      <span className="text-slate-400">{label}</span>
      <span className="text-right">{value}</span>
    </div>
  );
}

function nombreTipo(tipo: TipoVehiculoResponseDto["tipo"]) {
  switch (tipo) {
    case "CAR":
      return "Autos";
    case "MOTORCYCLE":
      return "Motos";
    case "BICYCLE":
      return "Bicicletas";
  }
}
