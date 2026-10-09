import {
  createBrowserRouter,
  Navigate,
} from "react-router-dom";

import AppLayout from "@/components/layout/AppLayout";

import ConfiguracionPage from "@/features/configuracion/ConfiguracionPage";

import PedidosPage from "@/features/pedidos/PedidosPage";

import EjecucionPage from "@/features/ejecucion/EjecucionPage";

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
          element: (
            <ConfiguracionPage />
          ),
        },

        {
          path: "/pedidos",
          element: (
            <PedidosPage />
          ),
        },

        {
          path: "/ejecucion/:ejecucionId",
          element: (
            <EjecucionPage />
          ),
        },

        /*
         * Evita abrir la ejecución
         * sin un ID válido.
         */
        {
          path: "/ejecucion",
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