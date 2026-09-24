#!/usr/bin/env python3
"""Paired summaries for PaqRap. Standard library is enough; SciPy/plots are opt-in.

Usage: python scripts/analyze.py results/my-run
       python scripts/analyze.py results/my-run --statistics --wilcoxon --plots

A search seed is a repetition of an instance, NOT a new independent instance.
Never rank an incomplete/invalid plan by its raw cost.
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
    if not value:
        return None
    number = float(value)
    return number if math.isfinite(number) else None


def ok(row: dict[str, str]) -> bool:
    return row.get("full_feasible") == "true"


def ok_unruled_out(row: dict[str, str]) -> bool:
    """Rutas validas que cubren todo pedido no descartado por la cota optimista (secundaria; igual a ok() si la cota no descarta pedidos).

    Corridas de versiones anteriores sin esa columna usan ok()."""
    value = row.get("full_unruled_out_feasible", "")
    return value == "true" if value else ok(row)


def average(values: list[float]) -> float | None:
    return st.mean(values) if values else None


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def sign_test(differences: list[float], tolerance: float = 1e-10) -> dict[str, Any]:
    """Two-sided conditional sign test; ties excluded, n=0 => p=1."""
    positive = sum(d > tolerance for d in differences)
    negative = sum(d < -tolerance for d in differences)
    n = positive + negative
    p = min(1.0, 2 * sum(math.comb(n, k) for k in range(min(positive, negative) + 1)) / 2**n) if n else 1.0
    return {"instances": len(differences), "positive": positive, "negative": negative,
            "ties": len(differences) - n, "p_two_sided": p}


def bootstrap_mean_interval(values: list[float], seed: int = 654321) -> list[float] | None:
    if len(values) < 2:
        return None
    rng = random.Random(seed)
    means = sorted(st.mean(rng.choices(values, k=len(values))) for _ in range(5000))
    return [means[int(0.025 * len(means))], means[int(0.975 * len(means))]]


def holm(p_values: list[float]) -> list[float]:
    order = sorted(range(len(p_values)), key=p_values.__getitem__)
    result = [1.0] * len(p_values)
    last = 0.0
    for rank, i in enumerate(order):
        last = max(last, min(1.0, (len(order) - rank) * p_values[i]))
        result[i] = last
    return result


def analyze(folder: Path, infer: bool, wilcoxon: bool, plots: bool) -> dict[str, Any]:
    with (folder / "runs.csv").open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        raise ValueError("No hay resultados en runs.csv")
    configs = {r["config_sha256"] for r in rows if r.get("config_sha256")}
    if len(configs) != 1:
        raise ValueError("Se detectaron configuraciones diferentes: no mezclar experimentos en un CSV")
    pairs: dict[tuple[str, str, str, str], dict[str, dict[str, str]]] = defaultdict(dict)
    for r in rows:
        key = (r["instance_id"], r["search_seed"], r["budget_ms"], r["mode"])
        if r["algorithm"] in pairs[key]:
            raise ValueError(f"Corrida duplicada para {key}, {r['algorithm']}")
        pairs[key][r["algorithm"]] = r
    if any(set(p) != {"GRASP", "SA"} for p in pairs.values()):
        raise ValueError("Hay parejas incompletas. Finaliza o reanuda el experimento antes de analizar.")
    paired_rows = []
    instance_pairs: dict[tuple[str, str, str], list[dict[str, Any]]] = defaultdict(list)
    for key, p in pairs.items():
        g, s = p["GRASP"], p["SA"]
        if g.get("input_sha256") != s.get("input_sha256"):
            raise ValueError(f"Los algoritmos no recibieron la misma instancia: {key}")
        cg, cs = num(g, "cost_complete_feasible"), num(s, "cost_complete_feasible")
        both = ok(g) and ok(s)
        if both and (cg is None or cs is None):
            raise ValueError("Corrida factible sin costo")
        log_ratio = math.log(cg / cs) if both and cg is not None and cs is not None and cg > 0 and cs > 0 else None
        both_unruled_out = ok_unruled_out(g) and ok_unruled_out(s)
        rg, rs = num(g, "cost_raw_do_not_rank_incomplete"), num(s, "cost_raw_do_not_rank_incomplete")
        record = {"instance_id": key[0], "family": g["family"], "search_seed": key[1], "budget_ms": key[2], "mode": key[3],
                  "orders_provably_unservable": g.get("orders_provably_unservable", ""),
                  "grasp_unruled_out_ok": int(ok_unruled_out(g)), "sa_unruled_out_ok": int(ok_unruled_out(s)),
                  # Descriptiva condicional: ambos cubren todo pedido no descartado por la cota optimista.
                  "log_cost_ratio_on_joint_unruled_out_success": math.log(rg / rs) if both_unruled_out and rg and rs and rg > 0 and rs > 0 else None,
                  "grasp_ok": int(ok(g)), "sa_ok": int(ok(s)), "joint_success": int(both),
                  "grasp_cost": cg, "sa_cost": cs,
                  "cost_difference_grasp_minus_sa": cg-cs if both and cg is not None and cs is not None else None,
                  "log_cost_ratio_grasp_over_sa": log_ratio,
                  "grasp_time_first_complete_ms": num(g, "time_first_complete_ms"),
                  "sa_time_first_complete_ms": num(s, "time_first_complete_ms"), "input_sha256": g["input_sha256"]}
        paired_rows.append(record)
        instance_pairs[(key[0], key[2], key[3])].append(record)
    per_instance = []
    for key, rr in instance_pairs.items():
        joint = [r for r in rr if r["joint_success"]]
        logs = [r["log_cost_ratio_grasp_over_sa"] for r in joint if r["log_cost_ratio_grasp_over_sa"] is not None]
        per_instance.append({"instance_id": key[0], "family": rr[0]["family"], "budget_ms": key[1], "mode": key[2],
                             "paired_repetitions": len(rr), "grasp_success_rate": st.mean(r["grasp_ok"] for r in rr),
                             "sa_success_rate": st.mean(r["sa_ok"] for r in rr),
                             "success_difference_grasp_minus_sa": st.mean(r["grasp_ok"]-r["sa_ok"] for r in rr),
                             "joint_success_repetitions": len(joint),
                             "mean_cost_difference_on_joint_success": average([r["cost_difference_grasp_minus_sa"] for r in joint]),
                             "mean_log_cost_ratio_on_joint_success": average(logs),
                             "all_repetitions_jointly_feasible": int(len(joint) == len(rr)),
                             "grasp_mean_cost_on_joint_success": average([r["grasp_cost"] for r in joint]),
                             "sa_mean_cost_on_joint_success": average([r["sa_cost"] for r in joint]),
                             "orders_provably_unservable": rr[0]["orders_provably_unservable"],
                             "grasp_unruled_out_success_rate": st.mean(r["grasp_unruled_out_ok"] for r in rr),
                             "sa_unruled_out_success_rate": st.mean(r["sa_unruled_out_ok"] for r in rr),
                             "mean_log_cost_ratio_on_joint_unruled_out_success": average(
                                 [r["log_cost_ratio_on_joint_unruled_out_success"] for r in rr if r["log_cost_ratio_on_joint_unruled_out_success"] is not None])})
    grouped: dict[tuple[str, str, str, str], list[dict[str, str]]] = defaultdict(list)
    for r in rows:
        grouped[(r["family"], r["algorithm"], r["budget_ms"], r["mode"])].append(r)
    summaries = []
    for key, rr in sorted(grouped.items()):
        costs = [v for r in rr if ok(r) and (v := num(r, "cost_complete_feasible")) is not None]
        elapsed = [v for r in rr if (v := num(r, "elapsed_ms")) is not None]
        first = [v for r in rr if ok(r) and (v := num(r, "time_first_complete_ms")) is not None]
        summaries.append({"family": key[0], "algorithm": key[1], "budget_ms": key[2], "mode": key[3], "runs": len(rr),
                          "instances": len({r["instance_id"] for r in rr}), "complete_feasible_runs": sum(ok(r) for r in rr),
                          "success_pct": 100 * sum(ok(r) for r in rr) / len(rr),
                          "mean_cost_on_success_not_directly_comparable": average(costs),
                          "median_cost_on_success": st.median(costs) if costs else None,
                          "sd_cost_on_success": st.stdev(costs) if len(costs)>1 else None,
                          "mean_elapsed_ms": average(elapsed), "mean_first_complete_ms_on_success": average(first),
                          "unruled_out_success_pct": 100 * sum(ok_unruled_out(r) for r in rr) / len(rr),
                          "mean_coverage_unruled_out_pct": average([v for r in rr if (v := num(r, "coverage_unruled_out_pct")) is not None]),
                          "mean_coverage_orders_pct": average([v for r in rr if (v := num(r, "coverage_orders_pct")) is not None])})
    out = folder / "analysis"
    out.mkdir(exist_ok=True)
    write_csv(out / "summary.csv", summaries)
    write_csv(out / "paired_runs.csv", paired_rows)
    write_csv(out / "per_instance.csv", per_instance)
    report: dict[str, Any] = {"runs": len(rows), "paired_runs": len(paired_rows), "independent_instance_labels": len({r['instance_id'] for r in rows}),
        "warnings": [
            "Las semillas son repeticiones dentro de instancia; no se tratan como instancias independientes.",
            "La factibilidad se compara con todas las corridas, incluidos los fallos. El costo se compara solo en parejas factibles.",
            "La inferencia de costo (opcional) usa SOLO instancias con todas sus repeticiones factibles en ambos algoritmos; su alcance es condicional a ese subconjunto.",
            "No se demuestra imposibilidad ni optimalidad. No extrapolar esta muestra a los tres escenarios operativos completos.",
            "Las medias de costo por algoritmo sobre sus propios éxitos pueden usar muestras distintas: NO elegir ganador con ellas.",
            "La independencia entre días reales no está garantizada. La inferencia es exploratoria para el conjunto de instancias definido.",
            "Métricas *_unruled_out_*: descriptivas y secundarias; excluyen únicamente pedidos descartados por la cota optimista Manhattan. No descartar un pedido NO prueba que sea atendible. La inferencia usa la métrica primaria.",
            "Revisar elapsed_ms y termination: los topes nativos o la falta de flota pueden terminar la búsqueda antes del presupuesto. En TIME, SA recalienta al llegar a Tmin; FIXED no sirve para comparar tiempos equivalentes.",
        ], "statistics_requested": infer, "tests": []}
    if infer:
        for mode, budget in sorted({(r["mode"],r["budget_ms"]) for r in per_instance}):
            rr = [r for r in per_instance if r["mode"]==mode and r["budget_ms"]==budget]
            success = [r["success_difference_grasp_minus_sa"] for r in rr]
            costs = [r["mean_log_cost_ratio_on_joint_success"] for r in rr if r["all_repetitions_jointly_feasible"] and r["mean_log_cost_ratio_on_joint_success"] is not None]
            for label, diff in [("success_rate_difference", success), ("mean_log_cost_ratio_complete_instances", costs)]:
                if not diff:
                    report["warnings"].append(f"Sin instancias elegibles para {label}, presupuesto {budget}.")
                    continue
                test = {"metric":label,"budget_ms":budget,"mode":mode,"mean_difference":st.mean(diff),
                        "median_difference":st.median(diff),"bootstrap_mean_95pct_instance_level":bootstrap_mean_interval(diff),**sign_test(diff)}
                if len(diff)<10:
                    test["small_sample_warning"]="Pocas instancias: análisis diagnóstico, no conclusión sólida."
                if label.startswith("mean_log"):
                    test["geometric_mean_cost_ratio_grasp_over_sa"]=math.exp(st.mean(diff))
                    try:
                        from scipy import stats
                        if len(diff)>=3 and max(diff)-min(diff)>1e-10:
                            normal=stats.shapiro(diff)
                            test["shapiro_on_paired_instance_differences"]={"W":float(normal.statistic),"p":float(normal.pvalue)}
                        if wilcoxon:
                            rounded=[round(d,10) for d in diff]
                            if all(abs(d)<1e-10 for d in rounded):test["wilcoxon_two_sided_p"]=1.0
                            else:test["wilcoxon_two_sided_p"]=float(stats.wilcoxon(rounded,zero_method="pratt",alternative="two-sided",method="auto").pvalue)
                            test["wilcoxon_assumption"]="Supone simetría pertinente de las diferencias; Shapiro no valida por sí solo este supuesto. Contraste exploratorio."
                    except ImportError:
                        report["warnings"].append("SciPy no instalado: se omitieron Shapiro y Wilcoxon, sin inventar p-valores.")
                report["tests"].append(test)
        adjusted=holm([t["p_two_sided"] for t in report["tests"]])
        for t,p in zip(report["tests"],adjusted):t["sign_test_p_holm_across_reported_metrics_and_budgets"]=p
    if plots:
        try:
            import matplotlib
            matplotlib.use("Agg")
            import matplotlib.pyplot as plt
            for mode,budget in sorted({(r["mode"],r["budget_ms"]) for r in summaries}):
                ss=[r for r in summaries if r['mode']==mode and r['budget_ms']==budget]
                families=sorted({r['family'] for r in ss});x=list(range(len(families)))
                fig,ax=plt.subplots(figsize=(9,5))
                for offset,alg in [(-.2,'GRASP'),(.2,'SA')]:
                    vals=[next((r['success_pct'] for r in ss if r['family']==f and r['algorithm']==alg),0) for f in families]
                    ax.bar([v+offset for v in x],vals,width=.38,label=alg)
                ax.set_xticks(x,families);ax.set_ylim(0,105);ax.set_ylabel('Corridas completas factibles (%)');ax.set_title(f'Factibilidad por familia · presupuesto {budget} ms');ax.legend();fig.tight_layout()
                fig.savefig(out/f'factibilidad-{mode}-{budget}.png',dpi=180);plt.close(fig)
                eligible=[r for r in per_instance if r['mode']==mode and r['budget_ms']==budget and r['all_repetitions_jointly_feasible']]
                if eligible:
                    fig,ax=plt.subplots(figsize=(6,6));gx=[r['grasp_mean_cost_on_joint_success'] for r in eligible];sy=[r['sa_mean_cost_on_joint_success'] for r in eligible]
                    ax.scatter(gx,sy);limit=max(gx+sy)*1.05;ax.plot([0,limit],[0,limit],linestyle='--');ax.set_xlabel('Costo medio GRASP (S/)');ax.set_ylabel('Costo medio SA (S/)');ax.set_title('Instancias con éxito en todas las repeticiones');fig.tight_layout();fig.savefig(out/f'costo-pareado-{mode}-{budget}.png',dpi=180);plt.close(fig)
        except ImportError:
            report['warnings'].append('Matplotlib no instalado: se omitieron los gráficos.')
    (out/'analysis.json').write_text(json.dumps(report,ensure_ascii=False,indent=2,allow_nan=False),encoding='utf-8')
    (out/'LEEME.txt').write_text('\n'.join(report['warnings'])+'\n\nNo copiar resultados de pruebas smoke al informe como evidencia definitiva.\n',encoding='utf-8')
    print(f"Listo: {len(rows)} corridas / {len(paired_rows)} parejas. Resultados en {out}")
    return report


def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder',type=Path)
    parser.add_argument('--statistics',action='store_true',help='Análisis exploratorio a nivel de instancia, no semillas independientes')
    parser.add_argument('--wilcoxon',action='store_true',help='Añade Wilcoxon bilateral; requiere considerar el supuesto de simetría')
    parser.add_argument('--plots',action='store_true')
    args=parser.parse_args()
    if args.wilcoxon and not args.statistics:parser.error('--wilcoxon requiere --statistics')
    analyze(args.folder,args.statistics,args.wilcoxon,args.plots)

if __name__=='__main__':
    main()
