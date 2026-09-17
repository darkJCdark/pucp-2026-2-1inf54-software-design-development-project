import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";

import {
  MOCK_ALMACENES,
  MOCK_INDICADORES,
} from "./ejecucion.mock";

export function PanelIndicadores() {
  const intermedios = MOCK_ALMACENES.filter(
    (almacen) => almacen.tipo === "INTERMEDIO",
  );

  return (
    <aside className="space-y-3 bg-[#11161d] p-3">
      <div className="grid grid-cols-2 gap-2">
        <Indicador
          titulo="Costo acumulado"
          valor={`S/ ${MOCK_INDICADORES.costoAcumulado.toLocaleString()}`}
        />

        <Indicador
          titulo="Distancia recorrida"
          valor={`${MOCK_INDICADORES.distanciaRecorrida} km`}
        />

        <Indicador
          titulo="Entregas completadas"
          valor={String(
            MOCK_INDICADORES.entregasCompletadas,
          )}
        />

        <Indicador
          titulo="Pedidos en riesgo"
          valor={String(
            MOCK_INDICADORES.pedidosRiesgo,
          )}
        />
      </div>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">
          Utilización de flota
        </h3>

        <UsoFlota
          nombre="Autos"
          actual={7}
          total={12}
        />

        <UsoFlota
          nombre="Motos"
          actual={13}
          total={20}
        />

        <UsoFlota
          nombre="Bicicletas"
          actual={7}
          total={10}
        />
      </Card>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">
          Velocidad de flota
        </h3>

        <Dato label="Autos" value="40 km/h" />
        <Dato label="Motos" value="25 km/h" />
        <Dato label="Bicicletas" value="12 km/h" />
      </Card>

      <Card className="border-slate-800 bg-[#151b23] p-4">
        <h3 className="mb-4 font-semibold">
          Almacenes intermedios
        </h3>

        {intermedios.map((almacen) => {
          const stock = almacen.stock ?? 0;
          const porcentajeDisponible =
            (stock / 1000) * 100;

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
                    porcentajeDisponible > 66
                      ? "rounded-full bg-green-500 px-2 py-1 text-[10px] text-black"
                      : porcentajeDisponible > 33
                        ? "rounded-full bg-amber-500 px-2 py-1 text-[10px] text-black"
                        : "rounded-full bg-red-500 px-2 py-1 text-[10px] text-white"
                  }
                >
                  {porcentajeDisponible > 66
                    ? "Verde"
                    : porcentajeDisponible > 33
                      ? "Ámbar"
                      : "Rojo"}
                </span>
              </div>

              <Progress
                value={porcentajeDisponible}
                className="h-2"
              />

              <div className="mt-1 flex justify-between text-[11px] text-slate-500">
                <span>{stock} disponibles</span>
                <span>de 1000 uds.</span>
              </div>
            </div>
          );
        })}
      </Card>
    </aside>
  );
}

interface IndicadorProps {
  titulo: string;
  valor: string;
}

function Indicador({
  titulo,
  valor,
}: IndicadorProps) {
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

interface UsoFlotaProps {
  nombre: string;
  actual: number;
  total: number;
}

function UsoFlota({
  nombre,
  actual,
  total,
}: UsoFlotaProps) {
  const porcentaje = (actual / total) * 100;

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

      <Progress value={porcentaje} className="h-2" />
    </div>
  );
}

interface DatoProps {
  label: string;
  value: string;
}

function Dato({
  label,
  value,
}: DatoProps) {
  return (
    <div className="flex items-center justify-between border-b border-slate-800 py-2 text-sm last:border-0">
      <span className="text-slate-400">{label}</span>
      <span>{value}</span>
    </div>
  );
}