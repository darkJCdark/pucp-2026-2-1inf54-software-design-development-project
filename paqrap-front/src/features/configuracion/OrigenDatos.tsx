import {
  useRef,
  useState,
  type ChangeEvent,
} from "react";

import {
  CheckCircle2,
  Database,
  FileCheck2,
  FileUp,
  Loader2,
  TriangleAlert,
} from "lucide-react";

import type { PeriodoCargadoResponseDto } from "@/api/pedidos.dto";
import { Card } from "@/components/ui/card";
import { useConfiguracion } from "@/context/ConfiguracionContext";

import {
  archivoVentasEsperado,
  periodoDesdeArchivoBloqueos,
  periodoDesdeArchivoVentas,
  periodoLegible,
} from "./periodos";

interface OrigenDatosProps {
  periodosCargados: PeriodoCargadoResponseDto[];
  mesesRequeridos: string[];
  mesesFaltantes: string[];
  consultandoPeriodos: boolean;
}

export function OrigenDatos({
  periodosCargados,
  mesesRequeridos,
  mesesFaltantes,
  consultandoPeriodos,
}: OrigenDatosProps) {
  const inputPedidosRef = useRef<HTMLInputElement>(null);
  const inputBloqueosRef = useRef<HTMLInputElement>(null);
  const [errorPedidos, setErrorPedidos] = useState<string | null>(null);
  const [errorBloqueos, setErrorBloqueos] = useState<string | null>(null);

  const {
    configuracion,
    actualizarConfiguracion,
    archivoPedidos,
    setArchivoPedidos,
    archivoBloqueos,
    setArchivoBloqueos,
  } = useConfiguracion();

  const usaDatosHistoricos = configuracion.escenario !== "DIA_A_DIA";

  function handlePedidos(event: ChangeEvent<HTMLInputElement>) {
    const archivo = event.target.files?.[0] ?? null;

    if (!archivo) {
      setArchivoPedidos(null);
      setErrorPedidos(null);
      return;
    }

    const periodo = periodoDesdeArchivoVentas(archivo.name);
    if (!periodo) {
      setArchivoPedidos(null);
      setErrorPedidos(
        "El archivo debe llamarse ventas.AAAAMM.txt, por ejemplo ventas.202603.txt.",
      );
      event.target.value = "";
      return;
    }

    setArchivoPedidos(archivo);
    setErrorPedidos(null);

    if (!configuracion.inicioSimulado) {
      actualizarConfiguracion({
        inicioSimulado: `${periodo}-01T00:00`,
      });
    }
  }

  function handleBloqueos(event: ChangeEvent<HTMLInputElement>) {
    const archivo = event.target.files?.[0] ?? null;

    if (!archivo) {
      setArchivoBloqueos(null);
      setErrorBloqueos(null);
      return;
    }

    if (!periodoDesdeArchivoBloqueos(archivo.name)) {
      setArchivoBloqueos(null);
      setErrorBloqueos(
        "El archivo debe llamarse bloqueo.AAMM.txt, por ejemplo bloqueo.2603.txt.",
      );
      event.target.value = "";
      return;
    }

    setArchivoBloqueos(archivo);
    setErrorBloqueos(null);
  }

  if (!usaDatosHistoricos) {
    return (
      <Card className="h-fit border-slate-800 bg-[#151b23] p-4">
        <h2 className="mb-5 font-semibold">Origen de datos</h2>
        <div className="rounded-md border border-slate-700 bg-[#11161d] p-4 text-sm text-slate-300">
          <p className="font-medium text-slate-200">Registro en línea</p>
          <p className="mt-2 text-xs leading-5 text-slate-500">
            En Día a Día los pedidos y las averías se registran durante la operación.
          </p>
        </div>
      </Card>
    );
  }

  const periodosPorNombre = new Map(periodosCargados.map((item) => [item.periodo, item]));

  return (
    <Card className="h-fit border-slate-800 bg-[#151b23] p-4">
      <h2 className="font-semibold">Origen de datos</h2>
      <p className="mt-1 text-xs leading-5 text-slate-500">
        El sistema usa primero los meses precargados en la base de datos. Solo debes subir ventas si falta cobertura.
      </p>

      <div className="mt-4 rounded-lg border border-slate-800 bg-[#11161d] p-3">
        <div className="flex items-center gap-2">
          <Database className="size-4 text-sky-400" />
          <p className="text-xs font-semibold uppercase text-slate-400">
            Cobertura requerida
          </p>
        </div>

        {consultandoPeriodos ? (
          <div className="mt-3 flex items-center gap-2 text-xs text-slate-400">
            <Loader2 className="size-3.5 animate-spin" />
            Consultando meses cargados...
          </div>
        ) : mesesRequeridos.length === 0 ? (
          <p className="mt-3 text-xs text-slate-500">
            Selecciona la fecha de inicio para comprobar la cobertura.
          </p>
        ) : (
          <ul className="mt-3 space-y-2">
            {mesesRequeridos.map((periodo) => {
              const cargado = periodosPorNombre.get(periodo);
              const cubiertoPorArchivo = periodoDesdeArchivoVentas(archivoPedidos?.name ?? "") === periodo;
              const disponible = Boolean(cargado) || cubiertoPorArchivo;

              return (
                <li
                  key={periodo}
                  className="flex items-center justify-between gap-3 rounded-md bg-slate-900/70 px-2.5 py-2"
                >
                  <div className="flex items-center gap-2">
                    {disponible ? (
                      <CheckCircle2 className="size-4 text-green-400" />
                    ) : (
                      <TriangleAlert className="size-4 text-amber-400" />
                    )}
                    <span className="text-xs text-slate-200">{periodoLegible(periodo)}</span>
                  </div>
                  <span className="text-[10px] text-slate-500">
                    {cargado
                      ? `${cargado.pedidos.toLocaleString("es-PE")} pedidos en BD`
                      : cubiertoPorArchivo
                        ? archivoPedidos?.name
                        : archivoVentasEsperado(periodo)}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <div className="mt-5">
        <ArchivoSelector
          titulo={mesesFaltantes.length > 0 ? "Pedidos requeridos" : "Pedidos (opcional)"}
          descripcion="ventas.AAAAMM.txt"
          archivo={archivoPedidos}
          inputRef={inputPedidosRef}
          onChange={handlePedidos}
          onAbrir={() => inputPedidosRef.current?.click()}
          ayuda={
            mesesFaltantes.length > 0
              ? `Falta: ${mesesFaltantes.map(archivoVentasEsperado).join(", ")}`
              : "La cobertura necesaria ya está disponible en la base de datos."
          }
          error={errorPedidos}
        />
      </div>

      <div className="mt-5">
        <ArchivoSelector
          titulo="Bloqueos (opcional)"
          descripcion="bloqueo.AAMM.txt"
          archivo={archivoBloqueos}
          inputRef={inputBloqueosRef}
          onChange={handleBloqueos}
          onAbrir={() => inputBloqueosRef.current?.click()}
          ayuda="Al cargar otro archivo del mismo mes, el backend reemplaza los bloqueos anteriores."
          error={errorBloqueos}
        />
      </div>
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
  ayuda,
  error,
}: {
  titulo: string;
  descripcion: string;
  archivo: File | null;
  inputRef: React.RefObject<HTMLInputElement | null>;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onAbrir: () => void;
  ayuda: string;
  error: string | null;
}) {
  return (
    <div>
      <p className="mb-2 text-xs font-semibold uppercase text-slate-400">{titulo}</p>

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
          "flex min-h-24 w-full flex-col items-center justify-center rounded-md border border-dashed px-4 transition",
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
          {archivo ? archivo.name : `Selecciona ${descripcion}`}
        </span>
        <span className="mt-1 text-[11px] text-slate-500">{ayuda}</span>
      </button>

      {error && <p className="mt-2 text-xs text-red-400">{error}</p>}
    </div>
  );
}
