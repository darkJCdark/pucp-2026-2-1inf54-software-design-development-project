import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  Route,
} from "lucide-react";

import type {
  PlanResponseDto,
  ReplanificacionResponseDto,
} from "@/api/planificacion.dto";

import {
  Card,
} from "@/components/ui/card";

interface PanelPlanificacionProps {
  plan: PlanResponseDto | null;
  replanificacion: ReplanificacionResponseDto | null;
  error: string | null;
}

export function PanelPlanificacion({
  plan,
  replanificacion,
  error,
}: PanelPlanificacionProps) {
  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold">
            Planificación SA
          </h3>

          <p className="mt-1 text-xs text-slate-500">
            Último plan vigente de la ejecución
          </p>
        </div>

        {plan && (
          <span
            className={[
              "rounded-full px-2 py-1 text-[10px] font-semibold",
              plan.factible
                ? "bg-green-500/15 text-green-400"
                : "bg-red-500/15 text-red-400",
            ].join(" ")}
          >
            {plan.factible
              ? "Factible"
              : "Con violaciones"}
          </span>
        )}
      </div>

      {!plan && !error && (
        <div className="rounded-md border border-slate-800 bg-[#11161d] p-3 text-xs text-slate-400">
          Esperando el primer plan de Simulated Annealing.
        </div>
      )}

      {error && (
        <div className="rounded-md border border-amber-900 bg-amber-950/20 p-3 text-xs text-amber-400">
          {error}
        </div>
      )}

      {plan && (
        <>
          <div className="grid grid-cols-2 gap-2">
            <MiniDato
              icono={<Route className="size-3.5" />}
              label="Rutas"
              value={String(plan.rutas.length)}
            />

            <MiniDato
              icono={<Clock3 className="size-3.5" />}
              label="Cómputo"
              value={`${plan.duracionMs} ms`}
            />

            <MiniDato
              label="Costo plan"
              value={`S/ ${plan.costoTotal.toFixed(2)}`}
            />

            <MiniDato
              label="Distancia plan"
              value={`${plan.distanciaTotalKm.toFixed(2)} km`}
            />
          </div>

          <div className="mt-3 flex flex-wrap gap-2 text-[10px]">
            <span className="rounded-full bg-slate-800 px-2 py-1 text-slate-300">
              Presupuesto: {plan.presupuestoMs} ms
            </span>

            {plan.presupuestoAgotado && (
              <span className="rounded-full bg-amber-500/15 px-2 py-1 text-amber-400">
                Presupuesto agotado
              </span>
            )}

            {replanificacion && (
              <span className="rounded-full bg-sky-500/15 px-2 py-1 text-sky-400">
                Replanificación #{replanificacion.numeroReplanificacion}
              </span>
            )}
          </div>

          {replanificacion && (
            <div className="mt-3 rounded-md bg-[#11161d] p-3 text-xs">
              <p className="font-medium text-slate-200">
                Motivo: {nombreMotivo(replanificacion.motivo)}
              </p>

              <p className="mt-1 text-slate-500">
                {replanificacion.rutasConservadas.length} rutas conservadas · {replanificacion.rutasNuevas.length} rutas nuevas
              </p>

              {replanificacion.pedidosReasignados.length > 0 && (
                <p className="mt-1 text-amber-400">
                  {replanificacion.pedidosReasignados.length} pedidos reasignados
                </p>
              )}
            </div>
          )}

          {plan.noAtendidos.length > 0 && (
            <div className="mt-3 rounded-md border border-amber-900 bg-amber-950/20 p-3">
              <div className="flex items-center gap-2 text-xs font-semibold text-amber-400">
                <AlertTriangle className="size-4" />
                Pedidos no atendidos: {plan.noAtendidos.length}
              </div>

              <p className="mt-1 text-[11px] text-slate-500">
                Que SA no los asigne en este ciclo no implica por sí solo colapso.
              </p>
            </div>
          )}

          {plan.violaciones.length === 0 ? (
            <div className="mt-3 flex items-center gap-2 text-xs text-green-400">
              <CheckCircle2 className="size-4" />
              El evaluador no reportó violaciones.
            </div>
          ) : (
            <div className="mt-3 space-y-2">
              {plan.violaciones.slice(0, 4).map((violacion, index) => (
                <div
                  key={`${violacion.tipo}-${violacion.rutaId ?? "global"}-${index}`}
                  className="rounded-md bg-red-950/20 p-2 text-[11px] text-red-300"
                >
                  <strong>{violacion.tipo}</strong>: {violacion.detalle}
                </div>
              ))}
            </div>
          )}

          {plan.rutas.length > 0 && (
            <div className="mt-4 max-h-52 space-y-2 overflow-y-auto pr-1">
              {plan.rutas.map((ruta) => (
                <div
                  key={ruta.id}
                  className="rounded-md border border-slate-800 bg-[#11161d] p-3 text-xs"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-semibold text-slate-200">
                      {ruta.vehiculoId}
                    </span>

                    <span className="text-slate-500">
                      {ruta.paradas.length} paradas
                    </span>
                  </div>

                  <div className="mt-2 flex justify-between text-[11px] text-slate-500">
                    <span>{ruta.distanciaKm.toFixed(2)} km</span>
                    <span>S/ {ruta.costo.toFixed(2)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </Card>
  );
}

function MiniDato({
  label,
  value,
  icono,
}: {
  label: string;
  value: string;
  icono?: React.ReactNode;
}) {
  return (
    <div className="rounded-md bg-[#11161d] p-2.5">
      <p className="flex items-center gap-1 text-[9px] uppercase text-slate-500">
        {icono}
        {label}
      </p>

      <p className="mt-1 text-sm font-semibold text-slate-200">
        {value}
      </p>
    </div>
  );
}

function nombreMotivo(
  motivo: ReplanificacionResponseDto["motivo"],
) {
  switch (motivo) {
    case "INICIO":
      return "Inicio";

    case "NUEVO_PEDIDO":
      return "Nuevo pedido";

    case "BLOQUEO":
      return "Bloqueo";

    case "AVERIA":
      return "Avería";

    case "PERIODICO":
      return "Ciclo periódico";
  }
}
