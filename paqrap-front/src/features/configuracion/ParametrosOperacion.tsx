import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";

import { useConfiguracion } from "@/context/ConfiguracionContext";
import type {
    Algoritmo,
    Escenario,
} from "./configuracion.types";
import { CampoConfiguracion } from "./CampoConfiguracion";

export function ParametrosOperacion() {
    const {
        configuracion,
        actualizarConfiguracion,
    } = useConfiguracion();

    const actualizarNumero = (
        campo: keyof typeof configuracion,
        valor: string,
    ) => {
        actualizarConfiguracion({
            [campo]: Number(valor),
        });
    };

    const cambiarEscenario = (escenario: Escenario) => {
        actualizarConfiguracion({
            escenario,
            compararAlgoritmos:
                escenario === "SIMULACION_5D"
                    ? configuracion.compararAlgoritmos
                    : false,
        });
    };

    return (
        <Card className="border-slate-800 bg-[#151b23] p-4">
            <h2 className="mb-5 font-semibold text-slate-100">
                Parámetros de la operación
            </h2>

            {/* ESCENARIO */}
            <p className="mb-2 text-xs font-semibold uppercase text-slate-400">
                Escenario de operación
            </p>

            <div className="mb-5 grid grid-cols-3 overflow-hidden rounded-md border border-slate-700">
                <BotonEscenario
                    label="Operación día a día"
                    active={
                        configuracion.escenario === "DIA_A_DIA"
                    }
                    onClick={() =>
                        cambiarEscenario("DIA_A_DIA")
                    }
                />

                <BotonEscenario
                    label="Simulación de periodo (5D)"
                    active={
                        configuracion.escenario ===
                        "SIMULACION_5D"
                    }
                    onClick={() =>
                        cambiarEscenario("SIMULACION_5D")
                    }
                />

                <BotonEscenario
                    label="Simulación de colapso logístico"
                    active={
                        configuracion.escenario === "COLAPSO"
                    }
                    onClick={() =>
                        cambiarEscenario("COLAPSO")
                    }
                />
            </div>

            {/* SWITCH DE COMPARACIÓN */}
            {configuracion.escenario === "SIMULACION_5D" && (
                <div className="mb-5 flex items-center gap-3">
                    <Switch
                        checked={configuracion.compararAlgoritmos}
                        onCheckedChange={(checked) =>
                            actualizarConfiguracion({
                                compararAlgoritmos: checked,
                            })
                        }
                    />

                    <span className="text-sm font-medium text-slate-200">
                        Ejecutar y comparar ambos algoritmos
                    </span>
                </div>
            )}

            <Separator className="mb-5 bg-slate-800" />

            {/* ======================================================
          MODO COMPARACIÓN
         ====================================================== */}
            {configuracion.compararAlgoritmos &&
                configuracion.escenario === "SIMULACION_5D" ? (
                <>
                    {/* Datos compartidos */}
                    <div className="grid gap-4 md:grid-cols-2">
                        <CampoConfiguracion label="Duración del periodo">
                            <Input
                                disabled
                                value={obtenerDuracion(
                                    configuracion.escenario,
                                )}
                                className="bg-slate-800/70"
                            />
                        </CampoConfiguracion>

                        <CampoConfiguracion label="Semilla de aleatoriedad (compartida)">
                            <Input
                                type="number"
                                value={configuracion.semilla}
                                onChange={(event) =>
                                    actualizarNumero(
                                        "semilla",
                                        event.target.value,
                                    )
                                }
                            />
                        </CampoConfiguracion>
                    </div>

                    {/* GRASP */}
                    <div className="mt-5 rounded-lg border border-slate-700 border-l-4 border-l-orange-500 bg-orange-500/[0.025] p-4">
                        <div className="mb-4 flex items-center gap-2">
                            <span className="size-2 rounded-full bg-orange-500" />

                            <h3 className="text-sm font-semibold text-slate-100">
                                GRASP
                            </h3>
                        </div>

                        <div className="grid gap-4 md:grid-cols-2">
                            <CampoConfiguracion label="Factor de aleatoriedad α (RCL)">
                                <Input
                                    type="number"
                                    min="0"
                                    max="1"
                                    step="0.01"
                                    value={configuracion.graspAlpha}
                                    onChange={(event) =>
                                        actualizarNumero(
                                            "graspAlpha",
                                            event.target.value,
                                        )
                                    }
                                />

                                <p className="mt-1 text-[11px] text-slate-500">
                                    0 = voraz · 1 = máxima exploración
                                </p>
                            </CampoConfiguracion>

                            <CampoConfiguracion label="Iteraciones máximas">
                                <Input
                                    type="number"
                                    min="1"
                                    value={configuracion.graspMaxIter}
                                    onChange={(event) =>
                                        actualizarNumero(
                                            "graspMaxIter",
                                            event.target.value,
                                        )
                                    }
                                />
                            </CampoConfiguracion>
                        </div>
                    </div>

                    {/* SIMULATED ANNEALING */}
                    <div className="mt-3 rounded-lg border border-slate-700 border-l-4 border-l-sky-500 bg-sky-500/[0.025] p-4">
                        <div className="mb-4 flex items-center gap-2">
                            <span className="size-2 rounded-full bg-sky-400" />

                            <h3 className="text-sm font-semibold text-slate-100">
                                Simulated Annealing
                            </h3>
                        </div>

                        <div className="grid gap-4 md:grid-cols-2">
                            <CampoConfiguracion label="Temperatura inicial (T)">
                                <Input
                                    type="number"
                                    min="1"
                                    value={
                                        configuracion.saTemperaturaInicial
                                    }
                                    onChange={(event) =>
                                        actualizarNumero(
                                            "saTemperaturaInicial",
                                            event.target.value,
                                        )
                                    }
                                />
                            </CampoConfiguracion>

                            <CampoConfiguracion label="Factor de enfriamiento (β)">
                                <Input
                                    type="number"
                                    min="0.8"
                                    max="0.99"
                                    step="0.01"
                                    value={
                                        configuracion.saFactorEnfriamiento
                                    }
                                    onChange={(event) =>
                                        actualizarNumero(
                                            "saFactorEnfriamiento",
                                            event.target.value,
                                        )
                                    }
                                />

                                <p className="mt-1 text-[11px] text-slate-500">
                                    T(k+1) = β × T(k) · rango 0.80–0.99
                                </p>
                            </CampoConfiguracion>

                            <CampoConfiguracion label="Iter. por nivel térmico (L)">
                                <Input
                                    type="number"
                                    min="1"
                                    value={
                                        configuracion.saIteracionesNivel
                                    }
                                    onChange={(event) =>
                                        actualizarNumero(
                                            "saIteracionesNivel",
                                            event.target.value,
                                        )
                                    }
                                />
                            </CampoConfiguracion>

                            <CampoConfiguracion label="Temperatura mínima (T mín)">
                                <Input
                                    type="number"
                                    min="0"
                                    value={
                                        configuracion.saTemperaturaMinima
                                    }
                                    onChange={(event) =>
                                        actualizarNumero(
                                            "saTemperaturaMinima",
                                            event.target.value,
                                        )
                                    }
                                />
                            </CampoConfiguracion>
                        </div>
                    </div>
                </>
            ) : (
                /* ======================================================
                   MODO INDIVIDUAL
                   ====================================================== */
                <>
                    <div className="grid gap-4 md:grid-cols-3">
                        <CampoConfiguracion label="Duración del periodo">
                            <Input
                                disabled
                                value={obtenerDuracion(
                                    configuracion.escenario,
                                )}
                                className="bg-slate-800/70"
                            />
                        </CampoConfiguracion>

                        <CampoConfiguracion label="Algoritmo metaheurístico">
                            <Select
                                value={configuracion.algoritmo}
                                onValueChange={(value) =>
                                    actualizarConfiguracion({
                                        algoritmo: value as Algoritmo,
                                    })
                                }
                            >
                                <SelectTrigger>
                                    <SelectValue />
                                </SelectTrigger>

                                <SelectContent>
                                    <SelectItem value="GRASP">
                                        GRASP
                                    </SelectItem>

                                    <SelectItem value="SIMULATED_ANNEALING">
                                        Simulated Annealing
                                    </SelectItem>
                                </SelectContent>
                            </Select>
                        </CampoConfiguracion>

                        <CampoConfiguracion label="Semilla de aleatoriedad">
                            <Input
                                type="number"
                                value={configuracion.semilla}
                                onChange={(event) =>
                                    actualizarNumero(
                                        "semilla",
                                        event.target.value,
                                    )
                                }
                            />
                        </CampoConfiguracion>
                    </div>

                    {/* GRASP INDIVIDUAL */}
                    {configuracion.algoritmo === "GRASP" && (
                        <div className="mt-6">
                            <p className="mb-3 text-xs font-semibold uppercase text-slate-400">
                                Parámetros de GRASP
                            </p>

                            <div className="grid gap-4 md:grid-cols-2">
                                <CampoConfiguracion label="Factor de aleatoriedad α (RCL)">
                                    <Input
                                        type="number"
                                        min="0"
                                        max="1"
                                        step="0.01"
                                        value={configuracion.graspAlpha}
                                        onChange={(event) =>
                                            actualizarNumero(
                                                "graspAlpha",
                                                event.target.value,
                                            )
                                        }
                                    />

                                    <p className="mt-1 text-[11px] text-slate-500">
                                        0 = voraz · 1 = máxima exploración
                                    </p>
                                </CampoConfiguracion>

                                <CampoConfiguracion label="Número máximo de iteraciones">
                                    <Input
                                        type="number"
                                        min="1"
                                        value={
                                            configuracion.graspMaxIter
                                        }
                                        onChange={(event) =>
                                            actualizarNumero(
                                                "graspMaxIter",
                                                event.target.value,
                                            )
                                        }
                                    />
                                </CampoConfiguracion>
                            </div>
                        </div>
                    )}

                    {/* SA INDIVIDUAL */}
                    {configuracion.algoritmo ===
                        "SIMULATED_ANNEALING" && (
                            <div className="mt-6">
                                <p className="mb-3 text-xs font-semibold uppercase text-slate-400">
                                    Parámetros de Simulated Annealing
                                </p>

                                <div className="grid gap-4 md:grid-cols-2">
                                    <CampoConfiguracion label="Temperatura inicial (T)">
                                        <Input
                                            type="number"
                                            min="1"
                                            value={
                                                configuracion.saTemperaturaInicial
                                            }
                                            onChange={(event) =>
                                                actualizarNumero(
                                                    "saTemperaturaInicial",
                                                    event.target.value,
                                                )
                                            }
                                        />
                                    </CampoConfiguracion>

                                    <CampoConfiguracion label="Factor de enfriamiento (β)">
                                        <Input
                                            type="number"
                                            min="0.8"
                                            max="0.99"
                                            step="0.01"
                                            value={
                                                configuracion.saFactorEnfriamiento
                                            }
                                            onChange={(event) =>
                                                actualizarNumero(
                                                    "saFactorEnfriamiento",
                                                    event.target.value,
                                                )
                                            }
                                        />
                                    </CampoConfiguracion>

                                    <CampoConfiguracion label="Iteraciones por nivel térmico (L)">
                                        <Input
                                            type="number"
                                            min="1"
                                            value={
                                                configuracion.saIteracionesNivel
                                            }
                                            onChange={(event) =>
                                                actualizarNumero(
                                                    "saIteracionesNivel",
                                                    event.target.value,
                                                )
                                            }
                                        />
                                    </CampoConfiguracion>

                                    <CampoConfiguracion label="Temperatura mínima (T mín)">
                                        <Input
                                            type="number"
                                            min="0"
                                            value={
                                                configuracion.saTemperaturaMinima
                                            }
                                            onChange={(event) =>
                                                actualizarNumero(
                                                    "saTemperaturaMinima",
                                                    event.target.value,
                                                )
                                            }
                                        />
                                    </CampoConfiguracion>
                                </div>
                            </div>
                        )}
                </>
            )}
        </Card>
    );
}

interface BotonEscenarioProps {
    label: string;
    active: boolean;
    onClick: () => void;
}

function BotonEscenario({
    label,
    active,
    onClick,
}: BotonEscenarioProps) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={
                active
                    ? "bg-orange-500 px-3 py-2.5 text-xs font-semibold text-white"
                    : "bg-[#151b23] px-3 py-2.5 text-xs text-slate-400 transition-colors hover:bg-slate-800 hover:text-slate-200"
            }
        >
            {label}
        </button>
    );
}

function obtenerDuracion(
    escenario: Escenario,
): string {
    switch (escenario) {
        case "DIA_A_DIA":
            return "Continua";

        case "SIMULACION_5D":
            return "5 días";

        case "COLAPSO":
            return "Hasta colapso";
    }
}