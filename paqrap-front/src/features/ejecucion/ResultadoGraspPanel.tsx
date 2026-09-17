//Esto me lo vuelo luego

import type { SolucionAlgoritmo } from "@/features/algoritmos/algoritmo.types";

interface ResultadoGraspPanelProps {
  solucion: SolucionAlgoritmo;
}

export function ResultadoGraspPanel({
  solucion,
}: ResultadoGraspPanelProps) {
  return (
    <div className="absolute bottom-5 left-5 z-40 w-80 rounded-lg border border-slate-700 bg-[#11161d]/95 p-4 text-white shadow-xl">
      <div className="mb-4">
        <p className="text-xs font-semibold uppercase text-orange-500">
          Resultado GRASP
        </p>

        <p
          className={`mt-1 text-sm font-semibold ${
            solucion.factible
              ? "text-green-400"
              : "text-red-400"
          }`}
        >
          {solucion.factible
            ? "✓ Solución factible"
            : "✕ Solución no factible"}
        </p>
      </div>

      <div className="mb-4 grid grid-cols-2 gap-2">
        <Dato
          titulo="Costo total"
          valor={`S/ ${solucion.costoTotal.toFixed(2)}`}
        />

        <Dato
          titulo="Distancia"
          valor={`${solucion.distanciaTotal.toFixed(2)} km`}
        />

        <Dato
          titulo="Rutas"
          valor={String(solucion.rutas.length)}
        />

        <Dato
          titulo="Sin asignar"
          valor={String(
            solucion.pedidosNoAsignados.length,
          )}
        />
      </div>

      <div className="max-h-60 space-y-3 overflow-y-auto pr-1">
        {solucion.rutas.map((ruta) => (
          <div
            key={ruta.vehiculoId}
            className="rounded-md border border-slate-800 bg-[#171d25] p-3"
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold">
                  {ruta.vehiculoId}
                </p>

                <p className="text-[11px] text-slate-400">
                  {ruta.tipoVehiculo}
                </p>
              </div>

              <div className="text-right text-[11px] text-slate-400">
                <p>{ruta.distanciaTotal.toFixed(1)} km</p>
                <p>S/ {ruta.costoTotal.toFixed(2)}</p>
              </div>
            </div>

            <div className="mt-3">
              <p className="mb-1 text-[10px] uppercase text-slate-500">
                Pedidos
              </p>

              {ruta.paradas.length > 0 ? (
                <p className="text-xs text-slate-200">
                  {ruta.paradas
                    .map((parada) => parada.pedidoId)
                    .join(" → ")}
                </p>
              ) : (
                <p className="text-xs text-slate-500">
                  Sin pedidos
                </p>
              )}
            </div>

            <div className="mt-2 text-[11px] text-slate-500">
              Carga: {ruta.cargaTotal}
            </div>
          </div>
        ))}
      </div>

      {solucion.pedidosNoAsignados.length > 0 && (
        <div className="mt-4 rounded-md border border-red-900 bg-red-950/30 p-3">
          <p className="text-xs font-semibold text-red-400">
            Pedidos no asignados
          </p>

          <p className="mt-1 text-xs text-slate-300">
            {solucion.pedidosNoAsignados
              .map((pedido) => pedido.id)
              .join(", ")}
          </p>
        </div>
      )}
    </div>
  );
}

interface DatoProps {
  titulo: string;
  valor: string;
}

function Dato({
  titulo,
  valor,
}: DatoProps) {
  return (
    <div className="rounded-md bg-[#202833] p-2">
      <p className="text-[10px] uppercase text-slate-500">
        {titulo}
      </p>

      <p className="mt-1 text-sm font-semibold">
        {valor}
      </p>
    </div>
  );
}