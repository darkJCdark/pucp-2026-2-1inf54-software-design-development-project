import {
  createBrowserRouter,
  Navigate,
} from "react-router-dom";

import AppLayout from "@/components/layout/AppLayout";

import ConfiguracionPage from "@/features/configuracion/ConfiguracionPage";
import EjecucionPage from "@/features/ejecucion/EjecucionPage";
import EjecucionesPage from "@/features/ejecuciones/EjecucionesPage";
import PedidosPage from "@/features/pedidos/PedidosPage";
import MantenimientosPage from "@/features/mantenimientos/MantenimientosPage";

export const router =
  createBrowserRouter([
    {
      element: <AppLayout />,
      children: [
        {
          path: "/",
          element: (
            <Navigate
              to="/simulacion"
              replace
            />
          ),
        },
        {
          path: "/simulacion",
          element: <ConfiguracionPage />,
        },
        {
          path: "/ejecuciones",
          element: <EjecucionesPage />,
        },
        {
          path: "/pedidos",
          element: <PedidosPage />,
        },
        {
          path: "/mantenimientos",
          element: <MantenimientosPage />,
        },
        {
          path: "/ejecucion/:ejecucionId",
          element: <EjecucionPage />,
        },
        {
          path: "/ejecucion",
          element: (
            <Navigate
              to="/ejecuciones"
              replace
            />
          ),
        },
        {
          path: "*",
          element: (
            <Navigate
              to="/simulacion"
              replace
            />
          ),
        },
      ],
    },
  ]);
