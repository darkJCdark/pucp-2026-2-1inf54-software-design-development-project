import type { ReactNode } from "react";

import { Loader2, X } from "lucide-react";

interface ConfirmDialogProps {
  abierto: boolean;
  titulo: string;
  descripcion?: string;
  children?: ReactNode;
  textoConfirmar?: string;
  textoCancelar?: string;
  confirmando?: boolean;
  variante?: "primary" | "danger";
  onConfirmar: () => void;
  onCancelar: () => void;
}

export function ConfirmDialog({
  abierto,
  titulo,
  descripcion,
  children,
  textoConfirmar = "Confirmar",
  textoCancelar = "Cancelar",
  confirmando = false,
  variante = "primary",
  onConfirmar,
  onCancelar,
}: ConfirmDialogProps) {
  if (!abierto) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-[180] flex items-center justify-center bg-black/65 p-4 backdrop-blur-sm">
      <div className="w-full max-w-lg overflow-hidden rounded-xl border border-slate-700 bg-[#11161d] text-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-800 px-5 py-4">
          <div>
            <h2 className="text-lg font-semibold">{titulo}</h2>
            {descripcion && (
              <p className="mt-1 text-sm leading-5 text-slate-400">{descripcion}</p>
            )}
          </div>

          <button
            type="button"
            onClick={onCancelar}
            disabled={confirmando}
            className="rounded p-1 text-slate-500 hover:bg-slate-800 hover:text-white disabled:opacity-50"
            aria-label="Cerrar"
          >
            <X className="size-5" />
          </button>
        </div>

        {children && <div className="px-5 py-4">{children}</div>}

        <div className="flex justify-end gap-2 border-t border-slate-800 px-5 py-4">
          <button
            type="button"
            onClick={onCancelar}
            disabled={confirmando}
            className="rounded-md border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800 disabled:opacity-50"
          >
            {textoCancelar}
          </button>

          <button
            type="button"
            onClick={onConfirmar}
            disabled={confirmando}
            className={[
              "flex items-center gap-2 rounded-md px-4 py-2 text-sm font-semibold text-white disabled:opacity-50",
              variante === "danger"
                ? "bg-red-700 hover:bg-red-600"
                : "bg-orange-500 hover:bg-orange-600",
            ].join(" ")}
          >
            {confirmando && <Loader2 className="size-4 animate-spin" />}
            {textoConfirmar}
          </button>
        </div>
      </div>
    </div>
  );
}
