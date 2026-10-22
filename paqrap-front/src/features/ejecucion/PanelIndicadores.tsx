import type {
  PlanResponseDto,
  ReplanificacionResponseDto,
} from "@/api/planificacion.dto";

import type {
  TipoVehiculoResponseDto,
} from "@/api/flota.dto";

import {
  Card,
} from "@/components/ui/card";

import {
  Progress,
} from "@/components/ui/progress";

import type {
  AlmacenMapa,
  IndicadoresOperacion,
  TipoVehiculo,
  VehiculoMapa,
} from "./ejecucion.types";

import {
  PanelPlanificacion,
} from "./PanelPlanificacion";

interface PanelIndicadoresProps {
  indicadores: IndicadoresOperacion;
  almacenes: AlmacenMapa[];
  vehiculos: VehiculoMapa[];
  tiposFlota: TipoVehiculoResponseDto[];
  plan: PlanResponseDto | null;
  replanificacion: ReplanificacionResponseDto | null;
  errorPlan: string | null;
}

export function PanelIndicadores({
  indicadores,
  almacenes,
  vehiculos,
  tiposFlota,
  plan,
  replanificacion,
  errorPlan,
}: PanelIndicadoresProps) {
  const intermedios =
    almacenes.filter(
      (almacen) =>
        almacen.tipo ===
        "INTERMEDIO",
    );

  const totalPorTipo = (
    tipo: TipoVehiculo,
  ) =>
    vehiculos.filter(
      (vehiculo) =>
        vehiculo.tipo === tipo,
    ).length;

  const enRuta = (
    tipo: TipoVehiculo,
  ) =>
    vehiculos.filter(
      (vehiculo) =>
        vehiculo.tipo === tipo &&
        vehiculo.estado ===
          "EN_RUTA",
    ).length;

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

        <Indicador
          titulo="Entregas completadas"
          valor={String(
            indicadores.entregasCompletadas,
          )}
        />

        <Indicador
          titulo="Pedidos en riesgo"
          valor={String(
            indicadores.pedidosRiesgo,
          )}
        />
      </div>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">
          Cumplimiento
        </h3>

        <Dato
          label="Entregados a tiempo"
          value={String(
            indicadores.entregasATiempo,
          )}
        />

        <Dato
          label="Paquetes entregados"
          value={String(
            indicadores.paquetesEntregados,
          )}
        />
      </Card>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">
          Utilización de flota
        </h3>

        <UsoFlota
          nombre="Autos"
          actual={enRuta("AUTO")}
          total={totalPorTipo("AUTO")}
        />

        <UsoFlota
          nombre="Motos"
          actual={enRuta("MOTO")}
          total={totalPorTipo("MOTO")}
        />

        <UsoFlota
          nombre="Bicicletas"
          actual={enRuta(
            "BICICLETA",
          )}
          total={totalPorTipo(
            "BICICLETA",
          )}
        />
      </Card>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">
          Parámetros de flota
        </h3>

        {tiposFlota.map((tipo) => (
          <Dato
            key={tipo.tipo}
            label={nombreTipo(
              tipo.tipo,
            )}
            value={`${tipo.velocidadKmh} km/h · S/ ${tipo.costoPorKm}/km`}
          />
        ))}
      </Card>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">
          Almacenes intermedios
        </h3>

        {intermedios.map(
          (almacen) => {
            const stock =
              almacen.stock ?? 0;

            const capacidad =
              almacen.capacidad ??
              1000;

            const porcentajeDisponible =
              capacidad > 0
                ? (stock /
                    capacidad) *
                  100
                : 0;

            return (
              <div
                key={almacen.id}
                className="mb-5 last:mb-0"
              >
                <div className="mb-2 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">
                      Almacén {almacen.nombre}
                    </p>

                    <p className="text-xs text-slate-500">
                      {almacen.id}
                    </p>
                  </div>

                  <span
                    className={
                      porcentajeDisponible >
                      66
                        ? "rounded-full bg-green-500 px-2 py-1 text-[10px] text-black"
                        : porcentajeDisponible >
                            33
                          ? "rounded-full bg-amber-500 px-2 py-1 text-[10px] text-black"
                          : "rounded-full bg-red-500 px-2 py-1 text-[10px] text-white"
                    }
                  >
                    {porcentajeDisponible >
                    66
                      ? "Verde"
                      : porcentajeDisponible >
                          33
                        ? "Ámbar"
                        : "Rojo"}
                  </span>
                </div>

                <Progress
                  value={
                    porcentajeDisponible
                  }
                  className="h-2"
                />

                <div className="mt-1 flex justify-between text-[11px] text-slate-500">
                  <span>
                    {stock} disponibles
                  </span>

                  <span>
                    de {capacidad} uds.
                  </span>
                </div>
              </div>
            );
          },
        )}
      </Card>

      <PanelPlanificacion
        plan={plan}
        replanificacion={
          replanificacion
        }
        error={errorPlan}
      />
    </aside>
  );
}

function Indicador({
  titulo,
  valor,
}: {
  titulo: string;
  valor: string;
}) {
  return (
    <Card className="border-slate-800 bg-[#151b23] p-3">
      <p className="text-[10px] uppercase text-slate-500">
        {titulo}
      </p>

      <p className="mt-2 text-xl font-semibold text-white">
        {valor}
      </p>
    </Card>
  );
}

function UsoFlota({
  nombre,
  actual,
  total,
}: {
  nombre: string;
  actual: number;
  total: number;
}) {
  const porcentaje =
    total > 0
      ? (actual / total) * 100
      : 0;

  return (
    <div className="mb-3">
      <div className="mb-1 flex justify-between text-xs">
        <span className="text-slate-400">
          {nombre}
        </span>

        <span className="text-slate-500">
          {actual}/{total} en ruta
        </span>
      </div>

      <Progress
        value={porcentaje}
        className="h-2"
      />
    </div>
  );
}

function Dato({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-slate-800 py-2 text-sm last:border-0">
      <span className="text-slate-400">
        {label}
      </span>

      <span className="text-right">
        {value}
      </span>
    </div>
  );
}

function nombreTipo(
  tipo: TipoVehiculoResponseDto["tipo"],
) {
  switch (tipo) {
    case "CAR":
      return "Autos";

    case "MOTORCYCLE":
      return "Motos";

    case "BICYCLE":
      return "Bicicletas";
  }
}
