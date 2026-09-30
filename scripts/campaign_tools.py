"""Shared standard-library helpers for experiment audit/calibration (Python 3.10+)."""
from __future__ import annotations
import csv, hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def properties(path: Path) -> dict[str, str]:
    result = {}
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        line = line.strip()
        if line and not line.startswith(('#', '!')):
            key, separator, value = line.partition('=')
            if not separator:
                raise ValueError(f'Formato no compatible en {path}: {line}')
            result[key.strip()] = value.strip()
    return result

def load_campaign(folder: Path, require_all: bool = True):
    """Reject duplicated/mixed inputs and unfinished grids. Failures remain observations."""
    metadata = json.loads((folder / 'metadata.json').read_text(encoding='utf-8'))
    with (folder / 'runs.csv').open(encoding='utf-8-sig', newline='') as f:
        rows = list(csv.DictReader(f))
    if not rows:
        raise ValueError('La campaña no tiene corridas.')
    with (folder / 'instances.csv').open(encoding='utf-8-sig', newline='') as f:
        specs = list(csv.DictReader(f))
    cfg = metadata['settings']
    expected = {(s['id'], seed.strip(), '0' if cfg.get('mode')=='FIXED' else budget.strip(), alg)
                for s in specs for seed in cfg['seeds'].split(',')
                for budget in cfg['budgets.ms'].split(',') for alg in ('GRASP', 'SA')}
    observed, inputs = set(), {}
    for row in rows:
        key = (row['instance_id'], row['search_seed'], row['budget_ms'], row['algorithm'])
        if key in observed:
            raise ValueError(f'Corrida duplicada: {key}')
        observed.add(key)
        if row['config_sha256'] != metadata['config_sha256']:
            raise ValueError('Se mezclaron configuraciones en runs.csv.')
        iid = row['instance_id']
        if inputs.setdefault(iid, row['input_sha256']) != row['input_sha256']:
            raise ValueError(f'Entradas diferentes para la misma instancia: {iid}')
        if row.get('algorithm_version') != metadata['algorithm_versions'][row['algorithm']]:
            raise ValueError('Se mezclaron versiones de los algoritmos.')
        complete = row.get('full_feasible') == 'true'
        if complete and (row.get('mandatory_meals_valid') != 'true' or row.get('route_constraints_valid') != 'true'):
            raise ValueError('Solución declarada completa sin descanso/rutas válidas.')
        if not complete and row.get('cost_complete_feasible'):
            raise ValueError('Un plan parcial no puede tener costo comparable.')
    if not observed <= expected or (require_all and observed != expected):
        raise ValueError(f'Cuadrícula incompleta o alterada: {len(observed)}/{len(expected)}. Reanuda sin cambiar la configuración.')
    return metadata, rows, specs
