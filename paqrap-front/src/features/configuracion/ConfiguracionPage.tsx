import { OrigenDatos } from "./OrigenDatos";
import { ParametrosOperacion } from "./ParametrosOperacion";
import { FlotaConfiguracion } from "./FlotaConfiguracion";
import { SemaforoConfiguracion } from "./SemaforoConfiguracion";
import { ResumenConfiguracion } from "./ResumenConfiguracion";

export default function ConfiguracionPage() {
  return (
    <div className="grid gap-3 p-4 xl:grid-cols-[250px_minmax(0,1fr)_270px]">
      <OrigenDatos />

      <section className="space-y-3">
        <ParametrosOperacion />
        <FlotaConfiguracion />
        <SemaforoConfiguracion />
      </section>

      <ResumenConfiguracion />
    </div>
  );
}