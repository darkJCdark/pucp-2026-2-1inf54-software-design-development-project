import {
  useState,
  type FormEvent,
} from "react";

import type {
  RegistrarPedidoRequestDto,
  TipoEntregaBackend,
} from "@/api/pedidos.dto";

interface RegistrarPedidoFormProps {
  onRegistrar: (
    pedido: RegistrarPedidoRequestDto,
  ) => Promise<void>;

  onCerrar: () => void;
}

export function RegistrarPedidoForm({
  onRegistrar,
  onCerrar,
}: RegistrarPedidoFormProps) {
  const [
    clienteId,
    setClienteId,
  ] = useState("");

  const [x, setX] =
    useState(30);

  const [y, setY] =
    useState(20);

  const [
    paquetes,
    setPaquetes,
  ] = useState(1);

  const [
    tipoEntrega,
    setTipoEntrega,
  ] =
    useState<TipoEntregaBackend>(
      "REGULAR",
    );

  const [
    horasPrometidas,
    setHorasPrometidas,
  ] = useState(4);

  const [
    enviando,
    setEnviando,
  ] = useState(false);

  const [
    error,
    setError,
  ] = useState<string | null>(
    null,
  );

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    setError(null);

    const cliente =
      clienteId.trim();

    if (
      !/^[A-Za-z][A-Za-z0-9]{0,31}$/.test(
        cliente,
      )
    ) {
      setError(
        "El cliente debe comenzar con una letra y contener hasta 32 caracteres alfanuméricos.",
      );

      return;
    }

    if (
      x < 0 ||
      x > 70 ||
      y < 0 ||
      y > 50
    ) {
      setError(
        "Las coordenadas deben encontrarse dentro de X=0..70 e Y=0..50.",
      );

      return;
    }

    if (paquetes <= 0) {
      setError(
        "La cantidad debe ser mayor que cero.",
      );

      return;
    }

    const request: RegistrarPedidoRequestDto =
      {
        clienteId:
          cliente,

        x,
        y,

        paquetes,

        tipoEntrega,

        ...(tipoEntrega ===
        "PRIORITY"
          ? {
              horasPrometidas,
            }
          : {}),
      };

    try {
      setEnviando(true);

      await onRegistrar(
        request,
      );
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "No fue posible registrar el pedido.",
      );
    } finally {
      setEnviando(false);
    }
  }

  function cambiarTipoEntrega(
    tipo: TipoEntregaBackend,
  ) {
    setTipoEntrega(tipo);

    if (
      tipo === "PRIORITY"
    ) {
      setHorasPrometidas(
        4,
      );
    }
  }

  return (
    <div className="absolute right-5 top-16 z-50 w-80 rounded-lg border border-slate-700 bg-[#11161d] p-5 text-white shadow-xl">
      <div className="mb-4">
        <h2 className="font-semibold">
          Registrar pedido
        </h2>

        <p className="mt-1 text-xs text-slate-400">
          El pedido será registrado
          en el backend y almacenado
          en MySQL.
        </p>
      </div>

      <form
        onSubmit={
          handleSubmit
        }
        className="space-y-4"
      >
        {/* Cliente */}
        <div>
          <label className="mb-1 block text-xs text-slate-400">
            Cliente
          </label>

          <input
            type="text"
            value={clienteId}
            maxLength={32}
            placeholder="c9167"
            onChange={(
              event,
            ) =>
              setClienteId(
                event.target
                  .value,
              )
            }
            className="w-full rounded-md border border-slate-700 bg-[#202833] px-3 py-2 text-sm text-white outline-none focus:border-orange-500"
          />
        </div>

        {/* Coordenadas */}
        <div className="grid grid-cols-2 gap-3">
          <CampoNumero
            label="Coordenada X"
            value={x}
            min={0}
            max={70}
            onChange={setX}
          />

          <CampoNumero
            label="Coordenada Y"
            value={y}
            min={0}
            max={50}
            onChange={setY}
          />
        </div>

        {/* Cantidad */}
        <CampoNumero
          label="Cantidad"
          value={paquetes}
          min={1}
          onChange={
            setPaquetes
          }
        />

        {/* Tipo */}
        <div>
          <label className="mb-1 block text-xs text-slate-400">
            Tipo de entrega
          </label>

          <select
            value={
              tipoEntrega
            }
            onChange={(
              event,
            ) =>
              cambiarTipoEntrega(
                event.target
                  .value as TipoEntregaBackend,
              )
            }
            className="w-full rounded-md border border-slate-700 bg-[#202833] px-3 py-2 text-sm text-white outline-none focus:border-orange-500"
          >
            <option value="REGULAR">
              Regular
            </option>

            <option value="PRIORITY">
              Priorizada
            </option>
          </select>
        </div>

        {/* Plazo */}
        <div>
          <label className="mb-1 block text-xs text-slate-400">
            Plazo de entrega
          </label>

          {tipoEntrega ===
          "REGULAR" ? (
            <>
              <input
                type="text"
                value="36 horas"
                disabled
                className="w-full cursor-not-allowed rounded-md border border-slate-700 bg-[#202833]/60 px-3 py-2 text-sm text-slate-400"
              />

              <p className="mt-1 text-[10px] text-slate-500">
                Los pedidos regulares
                tienen un plazo fijo
                de 36 horas.
              </p>
            </>
          ) : (
            <select
              value={
                horasPrometidas
              }
              onChange={(
                event,
              ) =>
                setHorasPrometidas(
                  Number(
                    event.target
                      .value,
                  ),
                )
              }
              className="w-full rounded-md border border-slate-700 bg-[#202833] px-3 py-2 text-sm text-white outline-none focus:border-orange-500"
            >
              <option value={4}>
                4 horas
              </option>

              <option value={8}>
                8 horas
              </option>

              <option value={12}>
                12 horas
              </option>

              <option value={18}>
                18 horas
              </option>
            </select>
          )}
        </div>

        {/* Error */}
        {error && (
          <div className="rounded-md border border-red-900 bg-red-950/40 px-3 py-2 text-xs text-red-400">
            {error}
          </div>
        )}

        {/* Acciones */}
        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={
              onCerrar
            }
            disabled={
              enviando
            }
            className="rounded-md border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800 disabled:opacity-50"
          >
            Cancelar
          </button>

          <button
            type="submit"
            disabled={
              enviando
            }
            className="rounded-md bg-orange-500 px-4 py-2 text-sm font-semibold text-white hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {enviando
              ? "Registrando..."
              : "Registrar"}
          </button>
        </div>
      </form>
    </div>
  );
}

interface CampoNumeroProps {
  label: string;

  value: number;

  min?: number;
  max?: number;

  onChange: (
    value: number,
  ) => void;
}

function CampoNumero({
  label,
  value,
  min,
  max,
  onChange,
}: CampoNumeroProps) {
  return (
    <div>
      <label className="mb-1 block text-xs text-slate-400">
        {label}
      </label>

      <input
        type="number"
        value={value}
        min={min}
        max={max}
        onChange={(
          event,
        ) =>
          onChange(
            Number(
              event.target
                .value,
            ),
          )
        }
        className="w-full rounded-md border border-slate-700 bg-[#202833] px-3 py-2 text-sm text-white outline-none focus:border-orange-500"
      />
    </div>
  );
}