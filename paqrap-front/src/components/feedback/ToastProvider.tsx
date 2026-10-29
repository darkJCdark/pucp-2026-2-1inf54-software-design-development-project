import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

import {
  CheckCircle2,
  Info,
  TriangleAlert,
  X,
  XCircle,
} from "lucide-react";

export type ToastVariant =
  | "success"
  | "error"
  | "warning"
  | "info";

export interface ToastInput {
  titulo: string;
  descripcion?: string;
  variante?: ToastVariant;
  duracionMs?: number;
}

interface ToastItem extends Required<Pick<ToastInput, "titulo" | "variante">> {
  id: number;
  descripcion?: string;
}

interface ToastContextValue {
  mostrarToast: (toast: ToastInput) => number;
  cerrarToast: (id: number) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const siguienteId = useRef(1);

  const cerrarToast = useCallback((id: number) => {
    setToasts((actuales) => actuales.filter((toast) => toast.id !== id));
  }, []);

  const mostrarToast = useCallback(
    ({
      titulo,
      descripcion,
      variante = "info",
      duracionMs = 4500,
    }: ToastInput) => {
      const id = siguienteId.current++;

      setToasts((actuales) => [
        ...actuales.slice(-3),
        { id, titulo, descripcion, variante },
      ]);

      window.setTimeout(() => cerrarToast(id), duracionMs);
      return id;
    },
    [cerrarToast],
  );

  const valor = useMemo(
    () => ({ mostrarToast, cerrarToast }),
    [mostrarToast, cerrarToast],
  );

  return (
    <ToastContext.Provider value={valor}>
      {children}

      <div className="pointer-events-none fixed right-4 top-4 z-[200] flex w-[min(390px,calc(100vw-2rem))] flex-col gap-2">
        {toasts.map((toast) => (
          <ToastCard key={toast.id} toast={toast} onCerrar={() => cerrarToast(toast.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const contexto = useContext(ToastContext);

  if (!contexto) {
    throw new Error("useToast debe usarse dentro de ToastProvider");
  }

  return contexto;
}

function ToastCard({
  toast,
  onCerrar,
}: {
  toast: ToastItem;
  onCerrar: () => void;
}) {
  const config = {
    success: {
      icono: <CheckCircle2 className="size-5 text-green-400" />,
      borde: "border-green-800/80",
      fondo: "bg-green-950/95",
    },
    error: {
      icono: <XCircle className="size-5 text-red-400" />,
      borde: "border-red-800/80",
      fondo: "bg-red-950/95",
    },
    warning: {
      icono: <TriangleAlert className="size-5 text-amber-400" />,
      borde: "border-amber-800/80",
      fondo: "bg-amber-950/95",
    },
    info: {
      icono: <Info className="size-5 text-sky-400" />,
      borde: "border-sky-800/80",
      fondo: "bg-slate-950/95",
    },
  }[toast.variante];

  return (
    <div
      className={`pointer-events-auto flex gap-3 rounded-lg border p-3 text-white shadow-2xl backdrop-blur ${config.borde} ${config.fondo}`}
    >
      <div className="mt-0.5 shrink-0">{config.icono}</div>

      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold">{toast.titulo}</p>
        {toast.descripcion && (
          <p className="mt-1 whitespace-pre-line text-xs leading-5 text-slate-300">
            {toast.descripcion}
          </p>
        )}
      </div>

      <button
        type="button"
        onClick={onCerrar}
        className="h-fit rounded p-1 text-slate-400 hover:bg-white/10 hover:text-white"
        aria-label="Cerrar aviso"
      >
        <X className="size-4" />
      </button>
    </div>
  );
}
