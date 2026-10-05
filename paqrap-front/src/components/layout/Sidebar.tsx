import { Package, Settings } from "lucide-react";
import { NavLink } from "react-router-dom";

const enlaces = [
  {
    nombre: "Simulación",
    ruta: "/simulacion",
    icono: Settings,
  },
  {
    nombre: "Pedidos",
    ruta: "/pedidos",
    icono: Package,
  },
];

export default function Sidebar() {
  return (
    <aside className="flex min-h-screen w-52 flex-col border-r border-slate-800 bg-[#11161d]">
      {/* Logo */}
      <div className="flex h-16 items-center gap-3 border-b border-slate-800 px-5">
        <div className="flex size-8 items-center justify-center rounded-md bg-orange-500 font-bold text-white">
          P
        </div>

        <span className="text-lg font-semibold text-white">
          PaqRap
        </span>
      </div>

      {/* Navegación */}
      <nav className="flex flex-col gap-1 p-2">
        {enlaces.map((enlace) => {
          const Icono = enlace.icono;

          return (
            <NavLink
              key={enlace.ruta}
              to={enlace.ruta}
              className={({ isActive }) =>
                [
                  "relative flex items-center gap-3 rounded-md px-3 py-3",
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

                  <span>{enlace.nombre}</span>
                </>
              )}
            </NavLink>
          );
        })}
      </nav>
    </aside>
  );
}