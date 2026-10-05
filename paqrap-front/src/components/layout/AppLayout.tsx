import { Outlet } from "react-router-dom";

import Sidebar from "./Sidebar";

export default function AppLayout() {
  return (
    <div className="flex min-h-screen bg-[#0d1117] text-slate-100">
      <Sidebar />

      <main className="min-w-0 flex-1 bg-[#0d1117]">
        <Outlet />
      </main>
    </div>
  );
}