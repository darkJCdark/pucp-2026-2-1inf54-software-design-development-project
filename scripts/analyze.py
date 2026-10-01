#!/usr/bin/env python3
"""Paired summaries for PaqRap.

Usage:
    python scripts/analyze.py results/my-run
    python scripts/analyze.py results/my-run --statistics --wilcoxon --plots

Important:
- A search seed is a repetition of an instance, NOT a new independent instance.
- Never rank an incomplete/invalid plan by its raw cost.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import random
import statistics as st
from collections import defaultdict
from pathlib import Path
from typing import Any


def num(row: dict[str, str], field: str) -> float | None:
    value = row.get(field, "")
    if value is None or value == "":
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def ok(row: dict[str, str]) -> bool:
    return row.get("full_feasible") == "true"


def ok_unruled_out(row: dict[str, str]) -> bool:
    """Secondary diagnostic.

    True when the run covers every order not discarded by the optimistic bound.
    Older runs without the column fall back to ok().
    """
    value = row.get("full_unruled_out_feasible", "")
    return value == "true" if value else ok(row)


def average(values: list[float]) -> float | None:
    return st.mean(values) if values else None


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return

    fieldnames: list[str] = []
    seen: set[str] = set()
    for row in rows:
        for key in row.keys():
            if key not in seen:
                seen.add(key)
                fieldnames.append(key)

    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def sign_test(differences: list[float], tolerance: float = 1e-10) -> dict[str, Any]:
    """Two-sided conditional sign test; ties excluded; n=0 => p=1."""
    positive = sum(d > tolerance for d in differences)
    negative = sum(d < -tolerance for d in differences)
    n = positive + negative

    if n:
        smaller = min(positive, negative)
        tail = sum(math.comb(n, k) for k in range(smaller + 1)) / (2**n)
        p = min(1.0, 2 * tail)
    else:
        p = 1.0

    return {
        "instances": len(differences),
        "positive": positive,
        "negative": negative,
        "ties": len(differences) - n,
        "p_two_sided": p,
    }


def bootstrap_mean_interval(
    values: list[float],
    seed: int = 654321,
    repetitions: int = 5000,
) -> list[float] | None:
    if len(values) < 2:
        return None

    rng = random.Random(seed)
    means = sorted(
        st.mean(rng.choices(values, k=len(values)))
        for _ in range(repetitions)
    )

    lo = int(0.025 * len(means))
    hi = min(len(means) - 1, int(0.975 * len(means)))
    return [means[lo], means[hi]]


def holm(p_values: list[float]) -> list[float]:
    if not p_values:
        return []

    order = sorted(range(len(p_values)), key=p_values.__getitem__)
    result = [1.0] * len(p_values)
    last = 0.0

    for rank, i in enumerate(order):
        adjusted = min(1.0, (len(order) - rank) * p_values[i])
        last = max(last, adjusted)
        result[i] = last

    return result


def analyze(
    folder: Path,
    infer: bool,
    wilcoxon: bool,
    plots: bool,
) -> dict[str, Any]:
    runs_path = folder / "runs.csv"
    if not runs_path.exists():
        raise FileNotFoundError(f"No existe {runs_path}")

    with runs_path.open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))

    if not rows:
        raise ValueError("No hay resultados en runs.csv")

    # ------------------------------------------------------------------
    # 1. Consistencia básica de la campaña
    # ------------------------------------------------------------------
    configs = {
        r["config_sha256"]
        for r in rows
        if r.get("config_sha256")
    }
    if len(configs) != 1:
        raise ValueError(
            "Se detectaron configuraciones diferentes: "
            "no mezclar experimentos en un CSV"
        )

    versions: dict[str, set[str]] = defaultdict(set)

    for row in rows:
        versions[row["algorithm"]].add(row.get("algorithm_version", ""))

        if ok(row):
            if row.get("route_constraints_valid") != "true":
                raise ValueError(
                    "Corrida completa sin validación de restricciones de ruta"
                )
            if row.get("mandatory_meals_valid") != "true":
                raise ValueError(
                    "Corrida completa sin validación de descansos obligatorios"
                )

        if not ok(row) and row.get("cost_complete_feasible", ""):
            raise ValueError(
                "Un plan incompleto no puede tener costo comparable"
            )

    if any(len(v) != 1 for v in versions.values()):
        raise ValueError(
            "Se detectaron versiones mezcladas del mismo algoritmo"
        )

    # ------------------------------------------------------------------
    # 2. Emparejar GRASP y SA por instancia, semilla, presupuesto y modo
    # ------------------------------------------------------------------
    pairs: dict[
        tuple[str, str, str, str],
        dict[str, dict[str, str]],
    ] = defaultdict(dict)

    for row in rows:
        key = (
            row["instance_id"],
            row["search_seed"],
            row["budget_ms"],
            row["mode"],
        )

        algorithm = row["algorithm"]
        if algorithm in pairs[key]:
            raise ValueError(
                f"Corrida duplicada para {key}, {algorithm}"
            )

        pairs[key][algorithm] = row

    incomplete_pairs = [
        key
        for key, pair in pairs.items()
        if set(pair) != {"GRASP", "SA"}
    ]
    if incomplete_pairs:
        raise ValueError(
            "Hay parejas incompletas. Finaliza o reanuda el experimento "
            f"antes de analizar. Ejemplo: {incomplete_pairs[0]}"
        )

    # ------------------------------------------------------------------
    # 3. Resultados pareados por corrida
    # ------------------------------------------------------------------
    paired_rows: list[dict[str, Any]] = []

    instance_pairs: dict[
        tuple[str, str, str],
        list[dict[str, Any]],
    ] = defaultdict(list)

    for key, pair in pairs.items():
        g = pair["GRASP"]
        s = pair["SA"]

        if g.get("input_sha256") != s.get("input_sha256"):
            raise ValueError(
                f"Los algoritmos no recibieron la misma instancia: {key}"
            )

        cg = num(g, "cost_complete_feasible")
        cs = num(s, "cost_complete_feasible")

        both = ok(g) and ok(s)

        if both and (cg is None or cs is None):
            raise ValueError(
                f"Pareja factible sin costo comparable: {key}"
            )

        log_ratio = (
            math.log(cg / cs)
            if (
                both
                and cg is not None
                and cs is not None
                and cg > 0
                and cs > 0
            )
            else None
        )

        record = {
            "instance_id": key[0],
            "family": g["family"],
            "search_seed": key[1],
            "budget_ms": key[2],
            "mode": key[3],

            "orders_provably_unservable":
                g.get("orders_provably_unservable", ""),

            "grasp_unruled_out_ok": int(ok_unruled_out(g)),
            "sa_unruled_out_ok": int(ok_unruled_out(s)),

            # No se compara costo de planes incompletos, incluso si ambos
            # cubren la demanda no descartada por la cota optimista.
            "log_cost_ratio_on_joint_unruled_out_success": None,

            "grasp_ok": int(ok(g)),
            "sa_ok": int(ok(s)),
            "joint_success": int(both),

            "grasp_cost": cg,
            "sa_cost": cs,

            "cost_difference_grasp_minus_sa":
                cg - cs
                if both and cg is not None and cs is not None
                else None,

            "log_cost_ratio_grasp_over_sa": log_ratio,

            "grasp_time_first_complete_ms":
                num(g, "time_first_complete_ms"),

            "sa_time_first_complete_ms":
                num(s, "time_first_complete_ms"),

            "grasp_coverage_orders_pct":
                num(g, "coverage_orders_pct"),

            "sa_coverage_orders_pct":
                num(s, "coverage_orders_pct"),

            # Distancia solo se compara cuando ambos planes son completos.
            "grasp_distance_km":
                num(g, "distance_km") if both else None,

            "sa_distance_km":
                num(s, "distance_km") if both else None,

            "input_sha256": g["input_sha256"],
        }

        paired_rows.append(record)

        # Las semillas son repeticiones de una misma instancia.
        instance_pairs[
            (key[0], key[2], key[3])
        ].append(record)

    # ------------------------------------------------------------------
    # 4. Agregación a nivel de instancia
    # ------------------------------------------------------------------
    per_instance: list[dict[str, Any]] = []

    for key, rr in instance_pairs.items():
        joint = [
            r for r in rr
            if r["joint_success"]
        ]

        logs = [
            r["log_cost_ratio_grasp_over_sa"]
            for r in joint
            if r["log_cost_ratio_grasp_over_sa"] is not None
        ]

        per_instance.append({
            "instance_id": key[0],
            "family": rr[0]["family"],
            "budget_ms": key[1],
            "mode": key[2],

            "paired_repetitions": len(rr),

            "grasp_success_rate": st.mean(
                r["grasp_ok"] for r in rr
            ),

            "sa_success_rate": st.mean(
                r["sa_ok"] for r in rr
            ),

            "success_difference_grasp_minus_sa": st.mean(
                r["grasp_ok"] - r["sa_ok"]
                for r in rr
            ),

            "joint_success_repetitions": len(joint),

            "mean_cost_difference_on_joint_success": average([
                r["cost_difference_grasp_minus_sa"]
                for r in joint
                if r["cost_difference_grasp_minus_sa"] is not None
            ]),

            "mean_log_cost_ratio_on_joint_success":
                average(logs),

            "all_repetitions_jointly_feasible":
                int(len(joint) == len(rr)),

            "grasp_mean_cost_on_joint_success": average([
                r["grasp_cost"]
                for r in joint
                if r["grasp_cost"] is not None
            ]),

            "sa_mean_cost_on_joint_success": average([
                r["sa_cost"]
                for r in joint
                if r["sa_cost"] is not None
            ]),

            "orders_provably_unservable":
                rr[0]["orders_provably_unservable"],

            "grasp_unruled_out_success_rate": st.mean(
                r["grasp_unruled_out_ok"]
                for r in rr
            ),

            "sa_unruled_out_success_rate": st.mean(
                r["sa_unruled_out_ok"]
                for r in rr
            ),

            "mean_log_cost_ratio_on_joint_unruled_out_success":
                average([
                    r["log_cost_ratio_on_joint_unruled_out_success"]
                    for r in rr
                    if r[
                        "log_cost_ratio_on_joint_unruled_out_success"
                    ] is not None
                ]),

            # Cobertura media de pedidos por instancia.
            "grasp_mean_coverage_pct": average([
                r["grasp_coverage_orders_pct"]
                for r in rr
                if r["grasp_coverage_orders_pct"] is not None
            ]),

            "sa_mean_coverage_pct": average([
                r["sa_coverage_orders_pct"]
                for r in rr
                if r["sa_coverage_orders_pct"] is not None
            ]),

            # Distancia solo cuando ambos algoritmos completaron.
            "grasp_mean_distance_on_joint_success": average([
                r["grasp_distance_km"]
                for r in joint
                if r["grasp_distance_km"] is not None
            ]),

            "sa_mean_distance_on_joint_success": average([
                r["sa_distance_km"]
                for r in joint
                if r["sa_distance_km"] is not None
            ]),

            # Tiempo hasta encontrar por primera vez una solución completa.
            "grasp_mean_first_complete_ms": average([
                r["grasp_time_first_complete_ms"]
                for r in rr
                if r["grasp_time_first_complete_ms"] is not None
            ]),

            "sa_mean_first_complete_ms": average([
                r["sa_time_first_complete_ms"]
                for r in rr
                if r["sa_time_first_complete_ms"] is not None
            ]),
        })

    # ------------------------------------------------------------------
    # 5. Resumen descriptivo por familia/algoritmo/presupuesto
    # ------------------------------------------------------------------
    grouped: dict[
        tuple[str, str, str, str],
        list[dict[str, str]],
    ] = defaultdict(list)

    for row in rows:
        grouped[
            (
                row["family"],
                row["algorithm"],
                row["budget_ms"],
                row["mode"],
            )
        ].append(row)

    summaries: list[dict[str, Any]] = []

    for key, rr in sorted(grouped.items()):
        costs = [
            v
            for r in rr
            if ok(r)
            and (
                v := num(r, "cost_complete_feasible")
            ) is not None
        ]

        elapsed = [
            v
            for r in rr
            if (v := num(r, "elapsed_ms")) is not None
        ]

        first = [
            v
            for r in rr
            if ok(r)
            and (
                v := num(r, "time_first_complete_ms")
            ) is not None
        ]

        summaries.append({
            "family": key[0],
            "algorithm": key[1],
            "budget_ms": key[2],
            "mode": key[3],

            "runs": len(rr),

            "instances": len({
                r["instance_id"]
                for r in rr
            }),

            "complete_feasible_runs":
                sum(ok(r) for r in rr),

            "success_pct":
                100 * sum(ok(r) for r in rr) / len(rr),

            # Esta media usa los éxitos propios del algoritmo y, por ello,
            # no debe usarse para decidir un ganador.
            "mean_cost_on_success_not_directly_comparable":
                average(costs),

            "median_cost_on_success":
                st.median(costs) if costs else None,

            "sd_cost_on_success":
                st.stdev(costs)
                if len(costs) > 1
                else None,

            "mean_elapsed_ms":
                average(elapsed),

            "mean_first_complete_ms_on_success":
                average(first),

            "unruled_out_success_pct":
                100
                * sum(ok_unruled_out(r) for r in rr)
                / len(rr),

            "mean_coverage_unruled_out_pct": average([
                v
                for r in rr
                if (
                    v := num(
                        r,
                        "coverage_unruled_out_pct",
                    )
                ) is not None
            ]),

            "mean_coverage_orders_pct": average([
                v
                for r in rr
                if (
                    v := num(
                        r,
                        "coverage_orders_pct",
                    )
                ) is not None
            ]),
        })

    # ------------------------------------------------------------------
    # 6. Archivos de salida
    # ------------------------------------------------------------------
    out = folder / "analysis"
    out.mkdir(exist_ok=True)

    write_csv(
        out / "summary.csv",
        summaries,
    )

    write_csv(
        out / "paired_runs.csv",
        paired_rows,
    )

    write_csv(
        out / "per_instance.csv",
        per_instance,
    )

    report: dict[str, Any] = {
        "runs": len(rows),
        "paired_runs": len(paired_rows),
        "independent_instance_labels": len({
            r["instance_id"]
            for r in rows
        }),
        "warnings": [
            (
                "Las semillas son repeticiones dentro de instancia; "
                "no se tratan como instancias independientes."
            ),
            (
                "La factibilidad se compara con todas las corridas, "
                "incluidos los fallos. El costo se compara únicamente "
                "en parejas factibles."
            ),
            (
                "La inferencia de costo usa únicamente instancias en "
                "las que todas sus repeticiones son factibles en ambos "
                "algoritmos; su alcance es condicional a ese subconjunto."
            ),
            (
                "No se demuestra imposibilidad ni optimalidad. "
                "No extrapolar esta muestra automáticamente a los tres "
                "escenarios operativos completos."
            ),
            (
                "Las medias de costo calculadas sobre los éxitos propios "
                "de cada algoritmo pueden usar muestras distintas; "
                "no elegir ganador con ellas."
            ),
            (
                "La independencia entre días reales no está garantizada. "
                "La inferencia es exploratoria para el conjunto de "
                "instancias definido."
            ),
            (
                "Las métricas *_unruled_out_* son descriptivas y "
                "secundarias. No descartar un pedido no demuestra "
                "que sea atendible."
            ),
            (
                "Revisar elapsed_ms y termination. En TIME, ambos "
                "algoritmos deben recibir el mismo presupuesto temporal."
            ),
        ],
        "statistics_requested": infer,
        "tests": [],
    }

    # ------------------------------------------------------------------
    # 7. Inferencia estadística opcional a nivel de instancia
    # ------------------------------------------------------------------
    if infer:
        combinations = sorted({
            (
                r["mode"],
                r["budget_ms"],
            )
            for r in per_instance
        })

        for mode, budget in combinations:
            rr = [
                r
                for r in per_instance
                if r["mode"] == mode
                and r["budget_ms"] == budget
            ]

            success = [
                r["success_difference_grasp_minus_sa"]
                for r in rr
            ]

            costs = [
                r["mean_log_cost_ratio_on_joint_success"]
                for r in rr
                if (
                    r["all_repetitions_jointly_feasible"]
                    and r[
                        "mean_log_cost_ratio_on_joint_success"
                    ] is not None
                )
            ]

            metrics = [
                (
                    "success_rate_difference",
                    success,
                ),
                (
                    "mean_log_cost_ratio_complete_instances",
                    costs,
                ),
            ]

            for label, diff in metrics:
                if not diff:
                    report["warnings"].append(
                        "Sin instancias elegibles para "
                        f"{label}, presupuesto {budget}."
                    )
                    continue

                test: dict[str, Any] = {
                    "metric": label,
                    "budget_ms": budget,
                    "mode": mode,
                    "mean_difference":
                        st.mean(diff),
                    "median_difference":
                        st.median(diff),
                    "bootstrap_mean_95pct_instance_level":
                        bootstrap_mean_interval(diff),
                    **sign_test(diff),
                }

                if len(diff) < 10:
                    test["small_sample_warning"] = (
                        "Pocas instancias: análisis diagnóstico, "
                        "no conclusión sólida."
                    )

                if label.startswith("mean_log"):
                    test[
                        "geometric_mean_cost_ratio_grasp_over_sa"
                    ] = math.exp(
                        st.mean(diff)
                    )

                try:
                    from scipy import stats

                    if (
                        len(diff) >= 3
                        and max(diff) - min(diff) > 1e-10
                    ):
                        normal = stats.shapiro(diff)
                        test[
                            "shapiro_on_paired_instance_differences"
                        ] = {
                            "W": float(
                                normal.statistic
                            ),
                            "p": float(
                                normal.pvalue
                            ),
                        }

                    if wilcoxon:
                        rounded = [
                            round(d, 10)
                            for d in diff
                        ]

                        if all(
                            abs(d) < 1e-10
                            for d in rounded
                        ):
                            test[
                                "wilcoxon_two_sided_p"
                            ] = 1.0
                        else:
                            result = stats.wilcoxon(
                                rounded,
                                zero_method="pratt",
                                alternative="two-sided",
                                method="auto",
                            )
                            test[
                                "wilcoxon_two_sided_p"
                            ] = float(
                                result.pvalue
                            )

                        test[
                            "wilcoxon_assumption"
                        ] = (
                            "Contraste exploratorio sobre diferencias "
                            "pareadas a nivel de instancia. La prueba "
                            "de Shapiro no valida por sí sola el supuesto "
                            "de simetría relevante para Wilcoxon."
                        )

                except ImportError:
                    report["warnings"].append(
                        "SciPy no instalado: se omitieron "
                        "Shapiro y Wilcoxon."
                    )

                report["tests"].append(test)

        adjusted = holm([
            t["p_two_sided"]
            for t in report["tests"]
        ])

        for test, p in zip(
            report["tests"],
            adjusted,
        ):
            test[
                "sign_test_p_holm_across_reported_metrics_and_budgets"
            ] = p

    # ------------------------------------------------------------------
    # 8. Gráficos opcionales
    # ------------------------------------------------------------------
    if plots:
        try:
            import matplotlib

            matplotlib.use("Agg")

            import matplotlib.pyplot as plt

            combinations = sorted({
                (
                    r["mode"],
                    r["budget_ms"],
                )
                for r in summaries
            })

            for mode, budget in combinations:
                ss = [
                    r
                    for r in summaries
                    if r["mode"] == mode
                    and r["budget_ms"] == budget
                ]

                # 1. Tasa de soluciones completas / factibilidad
                families = sorted({
                    r["family"]
                    for r in ss
                })

                x = list(range(len(families)))

                fig, ax = plt.subplots(figsize=(9, 5))

                for offset, alg in [
                    (-0.2, "GRASP"),
                    (0.2, "SA"),
                ]:
                    vals = [
                        next(
                            (
                                r["success_pct"]
                                for r in ss
                                if (
                                    r["family"] == family
                                    and r["algorithm"] == alg
                                )
                            ),
                            0,
                        )
                        for family in families
                    ]

                    ax.bar(
                        [value + offset for value in x],
                        vals,
                        width=0.38,
                        label=alg,
                    )

                ax.set_xticks(x, families)
                ax.set_ylim(0, 105)
                ax.set_ylabel(
                    "Corridas completas factibles (%)"
                )
                ax.set_title(
                    "Factibilidad por familia · "
                    f"presupuesto {budget} ms"
                )
                ax.legend()
                fig.tight_layout()

                fig.savefig(
                    out / f"factibilidad-{mode}-{budget}.png",
                    dpi=180,
                )
                plt.close(fig)

                instance_budget = [
                    r
                    for r in per_instance
                    if (
                        r["mode"] == mode
                        and r["budget_ms"] == budget
                    )
                ]

                eligible = [
                    r
                    for r in instance_budget
                    if r[
                        "all_repetitions_jointly_feasible"
                    ]
                ]

                # 2. Cobertura media de pedidos
                grasp_cov = [
                    r["grasp_mean_coverage_pct"]
                    for r in instance_budget
                    if r["grasp_mean_coverage_pct"] is not None
                ]

                sa_cov = [
                    r["sa_mean_coverage_pct"]
                    for r in instance_budget
                    if r["sa_mean_coverage_pct"] is not None
                ]

                if grasp_cov and sa_cov:
                    fig, ax = plt.subplots(figsize=(6, 5))

                    values = [
                        st.mean(grasp_cov),
                        st.mean(sa_cov),
                    ]

                    ax.bar(["GRASP", "SA"], values)
                    ax.set_ylim(0, 105)
                    ax.set_ylabel(
                        "Cobertura media de pedidos (%)"
                    )
                    ax.set_title(
                        "Cobertura media de pedidos · "
                        f"presupuesto {budget} ms"
                    )

                    for i, value in enumerate(values):
                        ax.text(
                            i,
                            min(value + 1, 103),
                            f"{value:.2f}%",
                            ha="center",
                        )

                    fig.tight_layout()
                    fig.savefig(
                        out / f"cobertura-{mode}-{budget}.png",
                        dpi=180,
                    )
                    plt.close(fig)

                # 3. Costo pareado GRASP vs SA
                if eligible:
                    gx = [
                        r["grasp_mean_cost_on_joint_success"]
                        for r in eligible
                    ]

                    sy = [
                        r["sa_mean_cost_on_joint_success"]
                        for r in eligible
                    ]

                    valid_pairs = [
                        (g, s)
                        for g, s in zip(gx, sy)
                        if g is not None and s is not None
                    ]

                    if valid_pairs:
                        gx_valid = [p[0] for p in valid_pairs]
                        sy_valid = [p[1] for p in valid_pairs]

                        fig, ax = plt.subplots(figsize=(6, 6))
                        ax.scatter(gx_valid, sy_valid)

                        low = min(gx_valid + sy_valid)
                        high = max(gx_valid + sy_valid)
                        margin = (
                            (high - low) * 0.05
                            if high > low
                            else max(high * 0.05, 1.0)
                        )

                        ax.plot(
                            [low - margin, high + margin],
                            [low - margin, high + margin],
                            linestyle="--",
                        )

                        ax.set_xlabel(
                            "Costo medio GRASP (S/)"
                        )
                        ax.set_ylabel(
                            "Costo medio SA (S/)"
                        )
                        ax.set_title(
                            "Costo pareado por instancia · "
                            f"presupuesto {budget} ms"
                        )

                        fig.tight_layout()
                        fig.savefig(
                            out
                            / f"costo-pareado-{mode}-{budget}.png",
                            dpi=180,
                        )
                        plt.close(fig)

                # 4. Boxplot de costos
                grasp_costs = [
                    r["grasp_mean_cost_on_joint_success"]
                    for r in eligible
                    if r["grasp_mean_cost_on_joint_success"] is not None
                ]

                sa_costs = [
                    r["sa_mean_cost_on_joint_success"]
                    for r in eligible
                    if r["sa_mean_cost_on_joint_success"] is not None
                ]

                if grasp_costs and sa_costs:
                    fig, ax = plt.subplots(figsize=(6, 5))

                    try:
                        ax.boxplot(
                            [grasp_costs, sa_costs],
                            tick_labels=["GRASP", "SA"],
                        )
                    except TypeError:
                        ax.boxplot(
                            [grasp_costs, sa_costs],
                            labels=["GRASP", "SA"],
                        )

                    ax.set_ylabel(
                        "Costo medio por instancia (S/)"
                    )
                    ax.set_title(
                        "Distribución de costos pareados · "
                        f"presupuesto {budget} ms"
                    )

                    fig.tight_layout()
                    fig.savefig(
                        out
                        / f"boxplot-costos-{mode}-{budget}.png",
                        dpi=180,
                    )
                    plt.close(fig)

                # 5. Distancia recorrida
                grasp_dist = [
                    r["grasp_mean_distance_on_joint_success"]
                    for r in eligible
                    if r[
                        "grasp_mean_distance_on_joint_success"
                    ] is not None
                ]

                sa_dist = [
                    r["sa_mean_distance_on_joint_success"]
                    for r in eligible
                    if r[
                        "sa_mean_distance_on_joint_success"
                    ] is not None
                ]

                if grasp_dist and sa_dist:
                    fig, ax = plt.subplots(figsize=(6, 5))

                    try:
                        ax.boxplot(
                            [grasp_dist, sa_dist],
                            tick_labels=["GRASP", "SA"],
                        )
                    except TypeError:
                        ax.boxplot(
                            [grasp_dist, sa_dist],
                            labels=["GRASP", "SA"],
                        )

                    ax.set_ylabel(
                        "Distancia media por instancia (km)"
                    )
                    ax.set_title(
                        "Distancia recorrida · "
                        f"presupuesto {budget} ms"
                    )

                    fig.tight_layout()
                    fig.savefig(
                        out
                        / f"distancia-{mode}-{budget}.png",
                        dpi=180,
                    )
                    plt.close(fig)

                # 6. Tiempo hasta primera solución completa
                grasp_first = [
                    r["grasp_mean_first_complete_ms"]
                    for r in instance_budget
                    if r[
                        "grasp_mean_first_complete_ms"
                    ] is not None
                ]

                sa_first = [
                    r["sa_mean_first_complete_ms"]
                    for r in instance_budget
                    if r[
                        "sa_mean_first_complete_ms"
                    ] is not None
                ]

                if grasp_first and sa_first:
                    fig, ax = plt.subplots(figsize=(6, 5))

                    try:
                        ax.boxplot(
                            [grasp_first, sa_first],
                            tick_labels=["GRASP", "SA"],
                        )
                    except TypeError:
                        ax.boxplot(
                            [grasp_first, sa_first],
                            labels=["GRASP", "SA"],
                        )

                    ax.set_ylabel(
                        "Tiempo hasta primera solución completa (ms)"
                    )
                    ax.set_title(
                        "Velocidad para encontrar solución completa · "
                        f"{budget} ms"
                    )

                    fig.tight_layout()
                    fig.savefig(
                        out
                        / (
                            "tiempo-primera-completa-"
                            f"{mode}-{budget}.png"
                        ),
                        dpi=180,
                    )
                    plt.close(fig)

        except ImportError:
            report["warnings"].append(
                "Matplotlib no instalado: se omitieron los gráficos."
            )

    # ------------------------------------------------------------------
    # 9. Reporte final
    # ------------------------------------------------------------------
    (out / "analysis.json").write_text(
        json.dumps(
            report,
            ensure_ascii=False,
            indent=2,
            allow_nan=False,
        ),
        encoding="utf-8",
    )

    (out / "LEEME.txt").write_text(
        "\n".join(report["warnings"])
        + (
            "\n\nNo copiar resultados de pruebas smoke "
            "al informe como evidencia definitiva.\n"
        ),
        encoding="utf-8",
    )

    print(
        f"Listo: {len(rows)} corridas / "
        f"{len(paired_rows)} parejas. "
        f"Resultados en {out}"
    )

    return report


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__,
    )

    parser.add_argument(
        "folder",
        type=Path,
        help="Carpeta de resultados que contiene runs.csv",
    )

    parser.add_argument(
        "--statistics",
        action="store_true",
        help=(
            "Análisis exploratorio a nivel de instancia; "
            "las semillas no se tratan como independientes"
        ),
    )

    parser.add_argument(
        "--wilcoxon",
        action="store_true",
        help=(
            "Añade Wilcoxon bilateral; "
            "requiere --statistics"
        ),
    )

    parser.add_argument(
        "--plots",
        action="store_true",
        help="Genera los gráficos PNG del análisis",
    )

    args = parser.parse_args()

    if args.wilcoxon and not args.statistics:
        parser.error(
            "--wilcoxon requiere --statistics"
        )

    analyze(
        args.folder,
        args.statistics,
        args.wilcoxon,
        args.plots,
    )


if __name__ == "__main__":
    main()
