import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router-dom";

import { ToastProvider } from "@/components/feedback/ToastProvider";
import { ConfiguracionProvider } from "@/context/ConfiguracionContext";
import { router } from "@/routes/router";

import "./index.css";

document.documentElement.classList.add("dark");

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ToastProvider>
      <ConfiguracionProvider>
        <RouterProvider router={router} />
      </ConfiguracionProvider>
    </ToastProvider>
  </React.StrictMode>,
);