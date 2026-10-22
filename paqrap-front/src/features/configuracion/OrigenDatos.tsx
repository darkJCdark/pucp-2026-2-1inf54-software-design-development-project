import {
  useRef,
  useState,
  type ChangeEvent,
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
  const inputPedidosRef =
    useRef<HTMLInputElement>(null);

  const inputBloqueosRef =
    useRef<HTMLInputElement>(null);

  const [errorPedidos, setErrorPedidos] =
    useState<string | null>(null);

  const [errorBloqueos, setErrorBloqueos] =
    useState<string | null>(null);

  const {
    configuracion,
    actualizarConfiguracion,
    archivoPedidos,
    setArchivoPedidos,
    archivoBloqueos,
    setArchivoBloqueos,
  } = useConfiguracion();

  const necesitaArchivo =
    configuracion.escenario !==
    "DIA_A_DIA";

  function handlePedidos(
    event: ChangeEvent<HTMLInputElement>,
  ) {
    const archivo =
      event.target.files?.[0] ?? null;

    if (!archivo) {
      setArchivoPedidos(null);
      setErrorPedidos(null);
      return;
    }

    const match =
      /^ventas\.(\d{4})(\d{2})\.txt$/i.exec(
        archivo.name,
      );

    if (!match) {
      setArchivoPedidos(null);
      setErrorPedidos(
        "El archivo debe llamarse ventas.AAAAMM.txt, por ejemplo ventas.202603.txt.",
      );
      event.target.value = "";
      return;
    }

    const anio = Number(match[1]);
    const mes = Number(match[2]);

    if (mes < 1 || mes > 12) {
      setArchivoPedidos(null);
      setErrorPedidos(
        "El mes del archivo no es válido.",
      );
      event.target.value = "";
      return;
    }

    setArchivoPedidos(archivo);
    setErrorPedidos(null);

    actualizarConfiguracion({
      inicioSimulado: `${anio}-${String(
        mes,
      ).padStart(2, "0")}-01T00:00`,
    });
  }

  function handleBloqueos(
    event: ChangeEvent<HTMLInputElement>,
  ) {
    const archivo =
      event.target.files?.[0] ?? null;

    if (!archivo) {
      setArchivoBloqueos(null);
      setErrorBloqueos(null);
      return;
    }

    const match =
      /^bloqueo\.(\d{2})(\d{2})\.txt$/i.exec(
        archivo.name,
      );

    if (!match) {
      setArchivoBloqueos(null);
      setErrorBloqueos(
        "El archivo debe llamarse bloqueo.AAMM.txt, por ejemplo bloqueo.2603.txt.",
      );
      event.target.value = "";
      return;
    }

    const mes = Number(match[2]);

    if (mes < 1 || mes > 12) {
      setArchivoBloqueos(null);
      setErrorBloqueos(
        "El mes del archivo de bloqueos no es válido.",
      );
      event.target.value = "";
      return;
    }

    setArchivoBloqueos(archivo);
    setErrorBloqueos(null);
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
            En Día a Día los pedidos y las averías se registran durante la operación.
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

      <ArchivoSelector
        titulo="Pedidos"
        descripcion="ventas.AAAAMM.txt"
        archivo={archivoPedidos}
        inputRef={inputPedidosRef}
        onChange={handlePedidos}
        onAbrir={() =>
          inputPedidosRef.current?.click()
        }
        obligatorio
        error={errorPedidos}
      />

      <div className="mt-5">
        <ArchivoSelector
          titulo="Bloqueos (opcional)"
          descripcion="bloqueo.AAMM.txt"
          archivo={archivoBloqueos}
          inputRef={inputBloqueosRef}
          onChange={handleBloqueos}
          onAbrir={() =>
            inputBloqueosRef.current?.click()
          }
          error={errorBloqueos}
        />
      </div>

      {archivoPedidos && (
        <div className="mt-4 rounded-md border border-slate-800 bg-[#11161d] p-3">
          <p className="text-[10px] uppercase text-slate-500">
            Inicio detectado
          </p>

          <p className="mt-1 text-sm text-slate-300">
            {configuracion.inicioSimulado}
          </p>
        </div>
      )}
    </Card>
  );
}

function ArchivoSelector({
  titulo,
  descripcion,
  archivo,
  inputRef,
  onChange,
  onAbrir,
  obligatorio = false,
  error,
}: {
  titulo: string;
  descripcion: string;
  archivo: File | null;
  inputRef: React.RefObject<HTMLInputElement | null>;
  onChange: (
    event: ChangeEvent<HTMLInputElement>,
  ) => void;
  onAbrir: () => void;
  obligatorio?: boolean;
  error: string | null;
}) {
  return (
    <div>
      <p className="mb-2 text-xs font-semibold uppercase text-slate-400">
        {titulo}
      </p>

      <input
        ref={inputRef}
        type="file"
        accept=".txt,text/plain"
        className="hidden"
        onChange={onChange}
      />

      <button
        type="button"
        onClick={onAbrir}
        className={[
          "flex min-h-28 w-full flex-col items-center justify-center rounded-md border border-dashed px-4 transition",
          archivo
            ? "border-green-700 bg-green-950/10"
            : "border-slate-700 bg-[#11161d] hover:border-orange-500",
        ].join(" ")}
      >
        {archivo ? (
          <FileCheck2 className="mb-2 size-6 text-green-400" />
        ) : (
          <FileUp className="mb-2 size-6 text-slate-400" />
        )}

        <span className="text-sm text-slate-300">
          {archivo
            ? archivo.name
            : `Selecciona ${descripcion}`}
        </span>

        <span className="mt-1 text-[11px] text-slate-500">
          {obligatorio
            ? "Obligatorio"
            : "Opcional"}
        </span>
      </button>

      {error && (
        <p className="mt-2 text-xs text-red-400">
          {error}
        </p>
      )}
    </div>
  );
}
