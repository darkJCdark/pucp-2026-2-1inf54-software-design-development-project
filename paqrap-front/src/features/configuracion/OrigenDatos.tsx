import {
  useRef,
  useState,
} from "react";

import {
  FileCheck2,
  FileUp,
} from "lucide-react";

import {
  Card,
} from "@/components/ui/card";

import {
  useConfiguracion,
} from "@/context/ConfiguracionContext";

export function OrigenDatos() {
  const inputRef =
    useRef<HTMLInputElement>(
      null,
    );

  const [
    errorArchivo,
    setErrorArchivo,
  ] = useState<string | null>(
    null,
  );

  const {
    configuracion,
    actualizarConfiguracion,
    archivoPedidos,
    setArchivoPedidos,
  } = useConfiguracion();

  const necesitaArchivo =
    configuracion.escenario !==
    "DIA_A_DIA";

  function handleArchivo(
    event: React.ChangeEvent<HTMLInputElement>,
  ) {
    const archivo =
      event.target.files?.[0] ??
      null;

    if (!archivo) {
      setArchivoPedidos(
        null,
      );

      setErrorArchivo(
        null,
      );

      return;
    }

    /*
     * Formato oficial:
     *
     * ventas.AAAAMM.txt
     *
     * Ejemplo:
     * ventas.202601.txt
     */
    const match =
      /^ventas\.(\d{4})(\d{2})\.txt$/i.exec(
        archivo.name,
      );

    if (!match) {
      setArchivoPedidos(
        null,
      );

      setErrorArchivo(
        "El archivo debe llamarse ventas.AAAAMM.txt, por ejemplo ventas.202601.txt.",
      );

      event.target.value =
        "";

      return;
    }

    const anio =
      Number(match[1]);

    const mes =
      Number(match[2]);

    if (
      mes < 1 ||
      mes > 12
    ) {
      setArchivoPedidos(
        null,
      );

      setErrorArchivo(
        "El mes indicado en el nombre del archivo no es válido.",
      );

      event.target.value =
        "";

      return;
    }

    setErrorArchivo(
      null,
    );

    setArchivoPedidos(
      archivo,
    );

    /*
     * Configuramos automáticamente
     * la simulación al comienzo del
     * mes contenido en el archivo.
     *
     * Ejemplo:
     *
     * ventas.202601.txt
     * →
     * 2026-01-01T00:00
     */
    const mesTexto =
      String(mes).padStart(
        2,
        "0",
      );

    actualizarConfiguracion({
      inicioSimulado:
        `${anio}-${mesTexto}-01T00:00`,
    });
  }

  if (!necesitaArchivo) {
    return (
      <Card className="h-fit border-slate-800 bg-[#151b23] p-4">
        <h2 className="mb-5 font-semibold">
          Origen de datos
        </h2>

        <div className="rounded-md border border-slate-700 bg-[#11161d] p-4 text-sm text-slate-300">
          <p className="font-medium text-slate-200">
            Registro en línea
          </p>

          <p className="mt-2 text-xs leading-5 text-slate-500">
            En Día a Día los pedidos
            se registran manualmente
            durante la operación.
          </p>
        </div>
      </Card>
    );
  }

  return (
    <Card className="h-fit border-slate-800 bg-[#151b23] p-4">
      <h2 className="mb-5 font-semibold">
        Origen de datos
      </h2>

      <p className="mb-3 text-xs font-semibold uppercase text-slate-400">
        Archivo de pedidos
      </p>

      <input
        ref={inputRef}
        type="file"
        accept=".txt,text/plain"
        className="hidden"
        onChange={
          handleArchivo
        }
      />

      <button
        type="button"
        onClick={() =>
          inputRef.current?.click()
        }
        className={[
          "flex min-h-32 w-full flex-col items-center justify-center rounded-md border border-dashed px-4 transition",
          archivoPedidos
            ? "border-green-700 bg-green-950/10"
            : "border-slate-700 bg-[#11161d] hover:border-orange-500",
        ].join(" ")}
      >
        {archivoPedidos ? (
          <FileCheck2 className="mb-3 size-6 text-green-400" />
        ) : (
          <FileUp className="mb-3 size-6 text-slate-400" />
        )}

        <span className="text-sm text-slate-300">
          {archivoPedidos
            ? archivoPedidos.name
            : "Selecciona ventas.AAAAMM.txt"}
        </span>

        <span className="mt-1 text-xs text-slate-500">
          Archivo mensual de pedidos
        </span>
      </button>

      {archivoPedidos && (
        <div className="mt-3 rounded-md border border-slate-800 bg-[#11161d] p-3">
          <p className="text-[10px] uppercase text-slate-500">
            Inicio detectado
          </p>

          <p className="mt-1 text-sm text-slate-300">
            {
              configuracion.inicioSimulado
            }
          </p>

          <p className="mt-1 text-[11px] text-slate-500">
            Puedes modificarlo
            manualmente si deseas comenzar
            más adelante dentro del mes.
          </p>
        </div>
      )}

      {errorArchivo && (
        <p className="mt-3 text-xs text-red-400">
          {errorArchivo}
        </p>
      )}

      {!archivoPedidos &&
        !errorArchivo && (
          <p className="mt-3 text-xs text-slate-500">
            Se requiere un archivo
            para 5D y Colapso.
          </p>
        )}
    </Card>
  );
}