import { MapaOperacion } from "./MapaOperacion";
import { PanelIndicadores } from "./PanelIndicadores";

export default function EjecucionPage() {
  return (
    <div className="grid h-screen grid-cols-[minmax(0,1fr)_330px] overflow-hidden bg-[#0d1117]">
      <MapaOperacion />
      <PanelIndicadores />
    </div>
  );
}