import { OrigenDatos } from "./OrigenDatos";
import { ParametrosOperacion } from "./ParametrosOperacion";
import { FlotaConfiguracion } from "./FlotaConfiguracion";
import { SemaforoConfiguracion } from "./SemaforoConfiguracion";
import { ResumenConfiguracion } from "./ResumenConfiguracion";
import { useConfiguracion } from "@/context/ConfiguracionContext";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";

export default function ConfiguracionPage() {
  const navigate = useNavigate();

  const {
    configuracion,
    archivoPedidos,
  } = useConfiguracion();

  const necesitaArchivo =
    configuracion.escenario !== "DIA_A_DIA";

  const puedeIniciar =
    !necesitaArchivo || archivoPedidos !== null;

  const handleIniciarSimulacion = () => {
    navigate("/ejecucion");
  };

  return (
    <div className="flex min-h-full flex-col">
      <div className="grid flex-1 gap-3 p-4 xl:grid-cols-[250px_minmax(0,1fr)_270px]">
        <OrigenDatos />

        <section className="space-y-3">
          <ParametrosOperacion />
          <FlotaConfiguracion />
          <SemaforoConfiguracion />
        </section>

        <ResumenConfiguracion />
      </div>

      <div className="sticky bottom-0 flex justify-end border-t border-slate-800 bg-[#0d1117] px-4 py-3">
        <Button
          type="button"
          disabled={!puedeIniciar}
          onClick={handleIniciarSimulacion}
          className="bg-[#ff6b35] text-white hover:bg-[#ff7b4d]"
        >
          Iniciar simulación
        </Button>
      </div>
    </div>
  );
}