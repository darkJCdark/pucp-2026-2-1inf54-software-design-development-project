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
import { ConfiguracionModoProvider } from "@/context/ConfiguracionContext";

export const router =
  createBrowserRouter([
    {
      element: <AppLayout />,
      children: [
        {
          path: "/",
          element: (
            <Navigate
              to="/operacion"
              replace
            />
          ),
        },
        {
          path: "/operacion",
          element: <ConfiguracionModoProvider modo="operacion"><ConfiguracionPage key="operacion" /></ConfiguracionModoProvider>,
        },
        {
          path: "/simulacion",
          element: <ConfiguracionModoProvider modo="simulacion"><ConfiguracionPage key="simulacion" /></ConfiguracionModoProvider>,
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
              to="/operacion"
              replace
            />
          ),
        },
      ],
    },
  ]);
