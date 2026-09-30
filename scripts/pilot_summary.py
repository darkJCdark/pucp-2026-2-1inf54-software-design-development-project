#!/usr/bin/env python3
"""Summarize all pilot budgets without picking a winner/budget automatically.
Usage: python scripts/pilot_summary.py results/pilot-01
Costs only on the SAME jointly complete instance-seed pairs at ALL budgets.
"""
from __future__ import annotations
import argparse, csv, json, statistics
from collections import defaultdict
from pathlib import Path
from campaign_tools import load_campaign

def summarize(folder: Path) -> list[dict]:
    meta, rows, _ = load_campaign(folder)
    budgets = sorted({int(r['budget_ms']) for r in rows})
    by_pair = defaultdict(list)
    for row in rows:
        by_pair[(row['instance_id'], row['search_seed'])].append(row)
    balanced_cost_keys = {k for k, rr in by_pair.items()
                          if len(rr) == 2 * len(budgets) and all(r['full_feasible'] == 'true' for r in rr)}
    result = []
    for budget in budgets:
        for alg in ('GRASP', 'SA'):
            rr = [r for r in rows if int(r['budget_ms']) == budget and r['algorithm'] == alg]
            costs = [float(r['cost_complete_feasible']) for r in rr
                     if (r['instance_id'], r['search_seed']) in balanced_cost_keys]
            valid = [r for r in rr if r.get('route_constraints_valid') == 'true']
            # Failure means zero credited coverage, not a dropped observation.
            coverage = [float(r.get('coverage_orders_pct') or 0) if r in valid else 0.0 for r in rr]
            elapsed = [float(r['elapsed_ms']) for r in rr if r.get('elapsed_ms')]
            result.append(dict(budget_ms=budget, algorithm=alg, runs=len(rr),
                complete_runs=sum(r['full_feasible']=='true' for r in rr),
                complete_pct=100*sum(r['full_feasible']=='true' for r in rr)/len(rr),
                route_valid_runs=len(valid), mean_coverage_pct=statistics.mean(coverage),
                worker_errors=sum(r['status'] in ('ERROR','WORKER_ERROR','HARD_TIMEOUT') for r in rr),
                cost_balanced_pairs=len(costs),
                mean_cost_balanced=statistics.mean(costs) if costs else '',
                mean_elapsed_ms=statistics.mean(elapsed) if elapsed else ''))
    out = folder/'analysis'; out.mkdir(exist_ok=True)
    with (out/'pilot_budgets.csv').open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(result[0]));writer.writeheader();writer.writerows(result)
    (out/'pilot_budgets.json').write_text(json.dumps({'budgets_ms':budgets,
        'balanced_instance_seed_pairs':len(balanced_cost_keys), 'summary':result,
        'notes':['Descriptivo: las semillas no son instancias independientes.',
                 'El costo usa las mismas parejas completas en TODOS los presupuestos; si no existen queda vacío.',
                 'Revisar cobertura, costo pareado, trazas y costo total de campaña antes de justificar el presupuesto.',
                 'No se elige automáticamente presupuesto ni algoritmo ganador.']},indent=2,ensure_ascii=False),encoding='utf-8')
    for row in result:
        print(f"{row['budget_ms']:>6} ms | {row['algorithm']:5} | completas {row['complete_runs']}/{row['runs']} | cobertura {row['mean_coverage_pct']:.1f}% | costo pareado {row['mean_cost_balanced']}")
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('folder',type=Path);args=parser.parse_args()
    try: summarize(args.folder)
    except (ValueError,OSError,KeyError) as e: parser.exit(1,f'ERROR: {e}\n')
