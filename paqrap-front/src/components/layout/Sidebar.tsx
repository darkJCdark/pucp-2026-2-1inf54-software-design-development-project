import {
  useState,
} from "react";

import {
  CalendarDays,
  History,
  Package,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
} from "lucide-react";

import {
  NavLink,
  useLocation,
} from "react-router-dom";

const enlaces = [
  {
    nombre: "Simulación",
    ruta: "/simulacion",
    icono: Settings,
  },
  {
    nombre: "Ejecuciones",
    ruta: "/ejecuciones",
    icono: History,
  },
  {
    nombre: "Pedidos",
    ruta: "/pedidos",
    icono: Package,
  },
  {
    nombre: "Mantenimientos",
    ruta: "/mantenimientos",
    icono: CalendarDays,
  },
];

/*
 * Barra lateral desplegable.
 * - Plegada: riel de 64 px con íconos (el nombre aparece como tooltip).
 * - En /ejecucion arranca plegada para dejar espacio al mapa;
 *   en las demás páginas arranca abierta.
 * - Si el usuario la cambia, se respeta mientras siga en ese tipo de página
 *   (estado en memoria, no se guarda al recargar).
 */
export default function Sidebar() {
  const {
    pathname,
  } = useLocation();

  const enEjecucion =
    pathname.startsWith(
      "/ejecucion",
    );

  const [
    preferencia,
    setPreferencia,
  ] =
    useState<{
      enEjecucion: boolean;
      plegada: boolean;
    } | null>(null);

  const plegada =
    preferencia?.enEjecucion ===
    enEjecucion
      ? preferencia.plegada
      : enEjecucion;

  function alternar() {
    setPreferencia({
      enEjecucion,
      plegada: !plegada,
    });
  }

  return (
    <aside
      className={[
        "flex min-h-screen shrink-0 flex-col border-r border-slate-800 bg-[#11161d] transition-[width] duration-150",
        plegada
          ? "w-16"
          : "w-52",
      ].join(" ")}
    >
      <div
        className={[
          "flex h-16 items-center border-b border-slate-800",
          plegada
            ? "justify-center px-2"
            : "justify-between gap-3 px-5",
        ].join(" ")}
      >
        {!plegada && (
          <div className="flex items-center gap-3">
            <div className="flex size-8 items-center justify-center rounded-md bg-orange-500 font-bold text-white">
              P
            </div>

            <span className="text-lg font-semibold text-white">
              PaqRap
            </span>
          </div>
        )}

        <button
          type="button"
          onClick={alternar}
          aria-expanded={!plegada}
          aria-label={plegada ? "Expandir menú" : "Plegar menú"}
          title={plegada ? "Expandir menú" : "Plegar menú"}
          className="rounded-md p-2 text-slate-400 hover:bg-slate-800 hover:text-white"
        >
          {plegada ? (
            <PanelLeftOpen className="size-4" />
          ) : (
            <PanelLeftClose className="size-4" />
          )}
        </button>
      </div>

      <nav className="flex flex-col gap-1 p-2">
        {enlaces.map((enlace) => {
          const Icono = enlace.icono;

          return (
            <NavLink
              key={enlace.ruta}
              to={enlace.ruta}
              title={plegada ? enlace.nombre : undefined}
              aria-label={enlace.nombre}
              className={({ isActive }) =>
                [
                  "relative flex items-center gap-3 rounded-md px-3 py-3",
                  plegada ? "justify-center" : "",
                  "text-sm font-medium transition-colors",
                  isActive
                    ? "bg-slate-800 text-white"
                    : "text-slate-400 hover:bg-slate-800/60 hover:text-white",
                ].join(" ")
              }
            >
              {({ isActive }) => (
                <>
                  {isActive && (
                    <span className="absolute -left-2 top-0 h-full w-1 rounded-r bg-orange-500" />
                  )}

                  <Icono
                    className={
                      isActive
                        ? "size-4 text-orange-500"
                        : "size-4"
                    }
                  />

                  {!plegada && (
                    <span>{enlace.nombre}</span>
                  )}
                </>
              )}
            </NavLink>
          );
        })}
      </nav>
    </aside>
  );
}
