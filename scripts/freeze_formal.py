#!/usr/bin/env python3
"""Freeze a formal configuration from a completed pilot, WITHOUT reusing its results.
Example (from module root):
python scripts/freeze_formal.py --pilot results/pilot-01 --budget-ms 10000 \
  --output config/formal-aprobado.properties --reason "Cobertura y costo se estabilizan; ver curvas del piloto."
Only copies calibrated parameters. Formal instance table and seeds remain distinct.
"""
from __future__ import annotations
import argparse, csv, json
from datetime import datetime, timezone
from pathlib import Path
from campaign_tools import ROOT, digest, properties, load_campaign

def freeze(pilot: Path, budget: int, output: Path, reason: str, template: Path) -> dict:
    if len(reason.strip()) < 20:
        raise ValueError('Escribe una justificación concreta de al menos 20 caracteres.')
    if output.exists() or output.with_suffix('.freeze.json').exists():
        raise ValueError('No sobrescribir una campaña congelada: elige otro nombre.')
    metadata, rows, pilot_specs = load_campaign(pilot)
    if metadata['settings'].get('campaign.stage') != 'PILOT':
        raise ValueError('Solo se acepta una campaña declarada PILOT, no smoke/readiness.')
    allowed = {int(b.strip()) for b in metadata['settings']['budgets.ms'].split(',')}
    if len(allowed) < 2 or budget not in allowed:
        raise ValueError('El piloto debe evaluar al menos dos presupuestos y el elegido debe estar entre ellos.')
    if any(r['status'] in ('ERROR','WORKER_ERROR','HARD_TIMEOUT') for r in rows):
        raise ValueError('Existen errores de ejecución: corrige y ejecuta un piloto nuevo antes de congelar.')
    jar = ROOT/'dist/paqrap-experimentos.jar'
    if metadata['classpath_sha256'].get(jar.name) != digest(jar):
        raise ValueError('El JAR cambió desde el piloto. Recalibra la versión que se va a presentar.')
    settings=properties(template)
    instances=ROOT/settings['instances.file']
    with instances.open(encoding='utf-8-sig',newline='') as f:
        formal_specs=list(csv.DictReader(f))
    # Compare scenario content even if someone renames its ID.
    signature=lambda r: tuple(r[k] for k in ('family','source','n_orders','instance_seed','date','from_hour','to_hour'))
    if {signature(r) for r in formal_specs} & {signature(r) for r in pilot_specs}:
        raise ValueError('Piloto y formal contienen instancias idénticas: separa calibración y evaluación.')
    keep={'name','instances.file','seeds','budgets.ms','campaign.stage','campaign.frozen'}
    for k,v in metadata['settings'].items():
        if k not in keep: settings[k]=v
    settings.update({'name':output.stem,'budgets.ms':str(budget),'campaign.stage':'FORMAL','campaign.frozen':'true'})
    # Detect changed on-disk data used by the pilot. No online lookup is required.
    for f in (pilot/'instances').glob('*.json'):
        for source, expected in json.loads(f.read_text(encoding='utf-8')).get('source_file_sha256',{}).items():
            path=ROOT/source
            if not path.is_file() or digest(path)!=expected:
                raise ValueError(f'Cambiaron los datos del piloto: {source}')
    output.parent.mkdir(parents=True,exist_ok=True)
    header='# Congelada desde piloto revisado. No modificar durante la campaña.\n'
    text=header+'\n'.join(f'{k}={v}' for k,v in sorted(settings.items()))+'\n'
    output.write_text(text,encoding='utf-8')
    info={'created_utc':datetime.now(timezone.utc).isoformat(),'reason':reason.strip(),
          'budget_ms':budget,'pilot_folder':str(pilot),'pilot_runs_sha256':digest(pilot/'runs.csv'),
          'pilot_metadata_sha256':digest(pilot/'metadata.json'),'jar_sha256':digest(jar),
          'formal_config_sha256':digest(output),'formal_instances_sha256':digest(instances),
          'formal_runs':len(formal_specs)*len(settings['seeds'].split(','))*2,
          'note':'Congelación de configuración; NO significa que la campaña formal se haya ejecutado ni demuestra superioridad.'}
    output.with_suffix('.freeze.json').write_text(json.dumps(info,indent=2,ensure_ascii=False),encoding='utf-8')
    return info

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--pilot',type=Path,required=True);p.add_argument('--budget-ms',type=int,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--reason',required=True)
    p.add_argument('--template',type=Path,default=ROOT/'config/formal.properties');a=p.parse_args()
    try:
        info=freeze(a.pilot,a.budget_ms,a.output,a.reason,a.template)
        print(f"Creada {a.output}: {info['formal_runs']} corridas a {a.budget_ms} ms. Aún no ejecutadas.")
    except (ValueError,OSError,KeyError) as e: p.exit(1,f'ERROR: {e}\n')
