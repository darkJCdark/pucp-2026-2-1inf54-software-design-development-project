import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";

import { useConfiguracion } from "@/context/ConfiguracionContext";
import { CampoConfiguracion } from "./CampoConfiguracion";

export function SemaforoConfiguracion() {
  const {
    configuracion,
    actualizarConfiguracion,
  } = useConfiguracion();

  return (
    <Card className="border-slate-800 bg-[#151b23] p-4">
      <h2 className="mb-5 font-semibold">
        Umbrales de semaforización
      </h2>

      <div className="grid gap-8 md:grid-cols-2">
        <div>
          <h3 className="mb-4 text-sm font-medium">
            Porcentaje despachado de almacenes intermedios
          </h3>

          <CampoConfiguracion label="Verde — hasta">
            <Input
              type="number"
              value={
                configuracion.almacenVerdeHasta
              }
              onChange={(event) =>
                actualizarConfiguracion({
                  almacenVerdeHasta: Number(
                    event.target.value,
                  ),
                })
              }
            />
          </CampoConfiguracion>

          <div className="mt-4">
            <CampoConfiguracion label="Ámbar — hasta">
              <Input
                type="number"
                value={
                  configuracion.almacenAmbarHasta
                }
                onChange={(event) =>
                  actualizarConfiguracion({
                    almacenAmbarHasta: Number(
                      event.target.value,
                    ),
                  })
                }
              />
            </CampoConfiguracion>
          </div>
        </div>

        <div>
          <h3 className="mb-4 text-sm font-medium">
            Estado de pedidos respecto a su plazo
          </h3>

          <CampoConfiguracion label="Verde — tiempo transcurrido ≤">
            <Input
              type="number"
              value={
                configuracion.pedidoVerdeHasta
              }
              onChange={(event) =>
                actualizarConfiguracion({
                  pedidoVerdeHasta: Number(
                    event.target.value,
                  ),
                })
              }
            />
          </CampoConfiguracion>

          <div className="mt-4">
            <CampoConfiguracion label="Ámbar — tiempo transcurrido ≤">
              <Input
                type="number"
                value={
                  configuracion.pedidoAmbarHasta
                }
                onChange={(event) =>
                  actualizarConfiguracion({
                    pedidoAmbarHasta: Number(
                      event.target.value,
                    ),
                  })
                }
              />
            </CampoConfiguracion>
          </div>
        </div>
      </div>
    </Card>
  );
}