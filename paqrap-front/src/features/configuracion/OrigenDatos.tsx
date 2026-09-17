import { useRef } from "react";
import { FileUp } from "lucide-react";

import { Card } from "@/components/ui/card";
import { useConfiguracion } from "@/context/ConfiguracionContext";

export function OrigenDatos() {
  const inputRef = useRef<HTMLInputElement>(null);

  const {
    configuracion,
    archivoPedidos,
    setArchivoPedidos,
  } = useConfiguracion();

  const necesitaArchivo =
    configuracion.escenario !== "DIA_A_DIA";

  const handleArchivo = (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    const archivo = event.target.files?.[0] ?? null;

    setArchivoPedidos(archivo);
  };

  return (
    <Card className="h-fit border-slate-800 bg-[#151b23] p-4">
      <h2 className="mb-5 font-semibold">
        Origen de datos
      </h2>

      {!necesitaArchivo ? (
        <div className="rounded-md border border-slate-700 bg-[#11161d] p-4 text-sm text-slate-300">
          Los pedidos se registrarán manualmente durante
          la operación.
        </div>
      ) : (
        <>
          <p className="mb-3 text-xs font-semibold uppercase text-slate-400">
            Archivo de pedidos del periodo
          </p>

          <input
            ref={inputRef}
            type="file"
            accept=".csv"
            className="hidden"
            onChange={handleArchivo}
          />

          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className="flex min-h-32 w-full flex-col items-center justify-center rounded-md border border-dashed border-slate-700 bg-[#11161d] px-4 transition hover:border-orange-500"
          >
            <FileUp className="mb-3 size-6 text-slate-400" />

            <span className="text-sm text-slate-300">
              {archivoPedidos
                ? archivoPedidos.name
                : "Arrastra un archivo .csv o haz clic"}
            </span>

            <span className="mt-1 text-xs text-slate-500">
              Conjunto de pedidos del periodo
            </span>
          </button>

          {!archivoPedidos && (
            <p className="mt-3 text-xs text-red-500">
              Carga el archivo de pedidos para continuar
            </p>
          )}
        </>
      )}
    </Card>
  );
}