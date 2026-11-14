import { useEffect, useState } from "react";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { incidenciasService } from "@/services/incidencias.service";
import { mensajeDeError } from "@/lib/api-error";
import { duracionMenorValida } from "./duracionAveria";

export function AveriaConfiguracion() {
  const [minutos, setMinutos] = useState(120);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [mensaje, setMensaje] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    let cancelado = false;
    void incidenciasService.obtenerConfiguracionMenor()
      .then((datos) => { if (!cancelado) { setMinutos(datos.duracionMinutos); setCargando(false); } })
      .catch((e) => { if (!cancelado) setError(mensajeDeError(e, "No se pudo cargar la configuración de averías.")); });
    return () => { cancelado = true; };
  }, []);
  async function guardar() {
    if (!duracionMenorValida(minutos)) { setError("Introduce minutos enteros positivos (máximo 2147483647)."); return; }
    setGuardando(true); setMensaje(""); setError("");
    try {
      const datos = await incidenciasService.actualizarConfiguracionMenor(minutos);
      setMinutos(datos.duracionMinutos);
      setMensaje("Guardado en el sistema. Solo afecta a futuras averías menores.");
    } catch (e) { setError(mensajeDeError(e, "No se pudo guardar.")); }
    finally { setGuardando(false); }
  }
  return <Card className="border-slate-800 bg-[#151b23] p-4">
    <h2 className="font-semibold">Avería menor · tipo 1</h2>
    <p className="my-3 text-xs text-slate-400">Duración de reparación. Predeterminado: 120 minutos. Mantiene posición y carga; las averías anteriores conservan su duración.</p>
    <label className="text-sm" htmlFor="minor-repair-minutes">Duración (minutos)</label>
    <Input id="minor-repair-minutes" type="number" min={1} max={2147483647} step={1}
      disabled={cargando || guardando} value={minutos}
      onChange={(e) => { setMinutos(Number(e.target.value)); setMensaje(""); }} />
    <Button type="button" className="mt-3" disabled={cargando || guardando || !duracionMenorValida(minutos)} onClick={() => void guardar()}>
      {guardando ? "Guardando…" : "Guardar duración"}
    </Button>
    {mensaje && <p role="status" className="mt-2 text-xs text-green-300">{mensaje}</p>}
    {error && <p role="alert" className="mt-2 text-xs text-red-300">{error}</p>}
  </Card>;
}
