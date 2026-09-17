import { useState } from "react";

import type { PedidoAlgoritmo } from "@/features/algoritmos/algoritmo.types";

interface RegistrarPedidoFormProps {
  siguienteNumero: number;
  onRegistrar: (pedido: PedidoAlgoritmo) => void;
  onCerrar: () => void;
}

export function RegistrarPedidoForm({
  siguienteNumero,
  onRegistrar,
  onCerrar,
}: RegistrarPedidoFormProps) {
  const [x, setX] = useState(30);
  const [y, setY] = useState(20);
  const [cantidad, setCantidad] = useState(1);
  const [plazoHoras, setPlazoHoras] = useState(4);

  const handleSubmit = (
    event: React.FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (x < 0 || x > 70) {
      return;
    }

    if (y < 0 || y > 50) {
      return;
    }

    if (cantidad <= 0) {
      return;
    }

    const pedido: PedidoAlgoritmo = {
      id: `PED-${String(siguienteNumero).padStart(
        3,
        "0",
      )}`,
      x,
      y,
      cantidad,
      plazoHoras,
    };

    onRegistrar(pedido);
  };

  return (
    <div className="absolute right-5 top-16 z-50 w-80 rounded-lg border border-slate-700 bg-[#11161d] p-5 text-white shadow-xl">
      <div className="mb-4">
        <h2 className="font-semibold">
          Registrar pedido
        </h2>

        <p className="mt-1 text-xs text-slate-400">
          El pedido será asignado considerando la
          disponibilidad actual de la flota.
        </p>
      </div>

      <form
        onSubmit={handleSubmit}
        className="space-y-4"
      >
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

        <CampoNumero
          label="Cantidad"
          value={cantidad}
          min={1}
          onChange={setCantidad}
        />

        <div>
          <label className="mb-1 block text-xs text-slate-400">
            Plazo de entrega
          </label>

          <select
            value={plazoHoras}
            onChange={(event) =>
              setPlazoHoras(
                Number(event.target.value),
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

            <option value={36}>
              36 horas
            </option>
          </select>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onCerrar}
            className="rounded-md border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800"
          >
            Cancelar
          </button>

          <button
            type="submit"
            className="rounded-md bg-orange-500 px-4 py-2 text-sm font-semibold text-white hover:bg-orange-600"
          >
            Registrar
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
  onChange: (value: number) => void;
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
        onChange={(event) =>
          onChange(
            Number(event.target.value),
          )
        }
        className="w-full rounded-md border border-slate-700 bg-[#202833] px-3 py-2 text-sm text-white outline-none focus:border-orange-500"
      />
    </div>
  );
}