#!/usr/bin/env python3
"""Independent checks on exported routes, without calling the Java scheduler.
Checks rest duration/window/location/non-overlap/count; street continuity/block timing;
capacity, exact conditioning duration, amounts, inventory, distance and cost. Does NOT
certify optimality or prove that missing orders are unservable.
Usage: python scripts/verify_results.py results/readiness-01
"""
from __future__ import annotations
import argparse, json, math
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from campaign_tools import load_campaign

HOUR=timedelta(hours=1)
LOCAL=timezone(timedelta(hours=-5))  # PaqRap case Lima, fixed UTC-05:00.

def instant(s: str) -> datetime:
    return datetime.fromisoformat(s.replace('Z','+00:00'))

def shift_start(t: datetime) -> datetime:
    t=t.astimezone(LOCAL)
    for hour in (23,15,7):
        if t.hour>=hour: return t.replace(hour=hour,minute=0,second=0,microsecond=0)
    return (t-timedelta(days=1)).replace(hour=23,minute=0,second=0,microsecond=0)

def overlapping(a,b,c,d):
    return a<d and c<b

def blocked_nodes(block: dict) -> set[tuple[int,int]]:
    result=set()
    for start,end in zip(block['nodes'],block['nodes'][1:]):
        x,y=start;tx,ty=end; dx=(tx>x)-(tx<x);dy=(ty>y)-(ty<y)
        result.add((x,y))
        while (x,y)!=(tx,ty):
            x+=dx;y+=dy;result.add((x,y))
    return result

def audit_plan(manifest: dict, document: dict, row: dict) -> dict:
    errors=[]
    def require(value,message):
        if not value: errors.append(message)
    audit=document['audit']
    vehicles={v['id']:v for v in manifest['fleet']}
    warehouses={w['id']:w for w in manifest['warehouses']}
    orders={o['id']:o for o in manifest['orders']}
    blocks=[(instant(b['from']),instant(b['to']),blocked_nodes(b)) for b in manifest['blocks']]
    served=Counter();total_dist=0;cost=0;meals_count=0;used=set();pickups=defaultdict(list)
    for route in audit['routes']:
        vid=route['vehicle_id'];require(vid in vehicles, 'Vehículo desconocido')
        if vid not in vehicles: continue
        require(vid not in used, 'Dos rutas simultáneas del mismo vehículo');used.add(vid)
        vehicle=vehicles[vid];position=(vehicle['x'],vehicle['y']);capacity=vehicle['capacity']
        departure=instant(route['departure']);now=departure;load=route['initial_load'];alllegs=[];service=[];distance=0
        require(0<=load<=capacity, 'Carga inicial inválida')
        require(len(route['stops'])==len(route['timetable']), 'Ruta sin horario completo')
        for stop,time in zip(route['stops'],route['timetable']):
            require(load==time['load_before'], 'Carga previa inconsistente')
            legs=time['path']
            require(time['approach_distance_km']==len(legs), 'Distancia del tramo inconsistente')
            for leg in legs:
                start=instant(leg['departure']);end=instant(leg['arrival'])
                source=tuple(leg['from']);target=tuple(leg['to'])
                require(source==position, 'Camino discontinuo')
                require(abs(source[0]-target[0])+abs(source[1]-target[1])==1, 'Tramo no unitario o diagonal')
                require(0<=target[0]<=70 and 0<=target[1]<=50, 'Nodo fuera del mapa')
                require(start>=now and end>start, 'Cronología de conducción inválida')
                require(abs((end-start).total_seconds()-3600/vehicle['speed'])<0.00001, 'Velocidad de tramo incorrecta')
                require(not any(a<=end<b and target in nodes for a,b,nodes in blocks), 'Entrada en nodo bloqueado')
                require(not any(a<=start<b and source in nodes for a,b,nodes in blocks), 'Salida de nodo bloqueado')
                alllegs.append((start,end,source,target));position=target;now=end;distance+=1
            arrival=instant(time['arrival']);start=instant(time['service_start']);end=instant(time['completion'])
            require(arrival>=now and start>=arrival and end>=start, 'Cronología de parada inválida')
            require(position==(stop['x'],stop['y']), 'Parada en ubicación incorrecta')
            if stop['type']=='DELIVERY':
                order=orders.get(stop['order_id']);require(order is not None,'Pedido desconocido')
                quantity=stop['packages'];require(quantity>0,'Entrega no positiva');load-=quantity
                require(end-start==HOUR, 'Acondicionamiento distinto de una hora')
                service.append((start,end))
                if order:
                    require(position==(order['x'],order['y']), 'Destino alterado')
                    require(instant(order['registered_at'])<=arrival<=instant(order['deadline']), 'Entrega fuera del plazo')
                    if instant(order['registered_at'])<=arrival<=instant(order['deadline']):served[order['id']]+=quantity
            else:
                require(stop['type']=='WAREHOUSE','Tipo de parada desconocido')
                warehouse=warehouses[stop['warehouse_id']];quantity=stop['pickup']
                require(position==(warehouse['x'],warehouse['y']), 'Almacén en coordenadas incorrectas')
                require(quantity>=0,'Recarga negativa');load+=quantity;pickups[warehouse['id']].append((arrival,quantity))
            require(0<=load<=capacity,'Carga fuera de capacidad o negativa')
            require(load==time['load_after'],'Carga posterior inconsistente');now=end
        require(route['stops'] and route['stops'][-1]['type']=='WAREHOUSE','Ruta sin retorno a almacén')
        returned=instant(route['returned_at']);done=instant(route['duty_completed_at'])
        require(returned==now and done>=returned,'Fin de recorrido/jornada incorrecto')
        counts=Counter();prior=None
        for meal in sorted(route['meal_breaks'],key=lambda b:b['start']):
            start=instant(meal['start']);end=instant(meal['end']);shift=shift_start(start)
            require(instant(meal['shift_start'])==shift,'Turno del descanso incorrecto')
            require(end-start==HOUR,'Descanso no dura una hora continua')
            require(start>=shift+HOUR and end<=shift+7*HOUR,'Descanso fuera del margen del turno')
            require(prior is None or start>=prior,'Descansos solapados');prior=end
            require(end<=done,'Fin de jornada anterior al descanso')
            if meal['placement']=='BEFORE_ROUTE':require(end<=departure,'Descanso previo termina después de la salida')
            if meal['placement']=='AFTER_ROUTE':require(start>=returned,'Descanso final anterior al retorno')
            expected=(vehicle['x'],vehicle['y'])
            for a,b,source,target in alllegs:
                require(not overlapping(start,end,a,b),'Conducción durante descanso')
                if b<=start:expected=target
            require(expected==(meal['x'],meal['y']),'Descanso en ubicación físicamente inconsistente')
            require(not any(overlapping(start,end,a,b) for a,b in service),'Acondicionamiento durante descanso')
            counts[shift]+=1;meals_count+=1
        required=set();shift=shift_start(departure)
        last=done-timedelta(microseconds=1) if done>departure else departure
        while shift<=last:
            required.add(shift);shift+=8*HOUR
        require(set(counts)==required and all(n==1 for n in counts.values()),'Falta/sobra descanso por turno')
        total_dist+=distance;cost+=distance*vehicle['cost_km']
    for wid,events in pickups.items():
        warehouse=warehouses[wid]
        if warehouse['central']: continue
        stock=warehouse['initial_stock'];last=instant(manifest['planning_time'])
        for time,qty in sorted(events):
            reset=last.astimezone(LOCAL).replace(hour=23,minute=59,second=59,microsecond=0)
            if reset<=last:reset+=timedelta(days=1)
            if reset<=time:stock=warehouse['capacity']
            stock-=qty;require(stock>=0,'Inventario intermedio negativo');last=time
    complete=sum(served[o['id']]==o['packages'] for o in orders.values())
    require(all(served[o['id']]<=o['packages'] for o in orders.values()),'Sobreentrega')
    require(complete==int(row['orders_fully_served']),'Conteo de pedidos inconsistente')
    require(sum(min(served[o['id']],o['packages']) for o in orders.values())==int(row['packages_covered_on_time']),'Unidades entregadas inconsistentes')
    require(math.isclose(total_dist,float(row['distance_km'])),'Distancia total incorrecta')
    require(math.isclose(cost,float(row['cost_raw_do_not_rank_incomplete'])),'Costo total incorrecto')
    require(meals_count==int(row['scheduled_route_meals']),'Conteo de descansos inconsistente')
    require((complete==len(orders) and not errors)==(row['full_feasible']=='true'), 'Clasificación completa incorrecta')
    require(document['run']['input_sha256']==row['input_sha256'],'Entrada distinta en CSV y plan')
    if errors: raise ValueError(row['run_id']+': '+'; '.join(sorted(set(errors))))
    return {'run_id':row['run_id'],'routes':len(used),'route_meals':meals_count,'complete':complete==len(orders),'distance_km':total_dist}

def verify(folder: Path) -> dict:
    _,rows,_=load_campaign(folder)
    checks=[];failures=[]
    for row in rows:
        if row.get('route_constraints_valid')!='true':
            failures.append({'run_id':row['run_id'],'status':row['status'],'note':'No se interpreta como solución válida.'});continue
        manifest=json.loads((folder/'instances'/f"{row['instance_id']}.json").read_text(encoding='utf-8'))
        plan=json.loads((folder/'jobs'/f"{row['run_id']}.plan.json").read_text(encoding='utf-8'))
        checks.append(audit_plan(manifest,plan,row))
    result={'checked_plans':len(checks),'invalid_or_failed_runs_retained':failures,'route_meals_checked':sum(r['route_meals'] for r in checks),
            'result':'PASS','checks':checks,'scope':'Verificación independiente de exportaciones, no prueba de optimalidad.'}
    (folder/'independent_verification.json').write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf-8')
    print(f"PASS: {len(checks)} planes; {result['route_meals_checked']} descansos de rutas; {len(failures)} fallos/planes no válidos conservados.")
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('folder',type=Path);args=p.parse_args()
    try:verify(args.folder)
    except (ValueError,OSError,KeyError,TypeError) as e:p.exit(1,f'ERROR: {e}\n')
