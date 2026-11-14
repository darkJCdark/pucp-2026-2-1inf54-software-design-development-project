package com.pucp.paqrap.modulos.escenarios.service;

import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import com.pucp.paqrap.config.EscenariosProperties;
import com.pucp.paqrap.modulos.escenarios.entity.*;
import com.pucp.paqrap.modulos.escenarios.persistence.*;
import com.pucp.paqrap.modulos.escenarios.repository.*;
import com.pucp.paqrap.modulos.escenarios.service.*;
import com.pucp.paqrap.modulos.flota.entity.*;
import com.pucp.paqrap.modulos.flota.persistence.*;
import com.pucp.paqrap.modulos.flota.repository.*;
import com.pucp.paqrap.modulos.almacenes.persistence.*;
import com.pucp.paqrap.modulos.almacenes.repository.*;
import com.pucp.paqrap.modulos.almacenes.service.*;
import com.pucp.paqrap.modulos.pedidos.entity.*;
import com.pucp.paqrap.modulos.pedidos.persistence.*;
import com.pucp.paqrap.modulos.pedidos.repository.*;
import com.pucp.paqrap.modulos.pedidos.service.*;
import com.pucp.paqrap.modulos.pedidos.dto.*;
import com.pucp.paqrap.modulos.redvial.entity.*;
import com.pucp.paqrap.modulos.redvial.persistence.*;
import com.pucp.paqrap.modulos.redvial.repository.*;
import com.pucp.paqrap.modulos.redvial.service.*;
import com.pucp.paqrap.modulos.incidencias.repository.*;
import com.pucp.paqrap.modulos.planificacion.service.*;
import com.pucp.paqrap.modulos.planificacion.dto.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.data.domain.*;
import jakarta.persistence.EntityManager;

/** Regresión determinista de motor/estado/importadores con repositorios dobles.
 * Las pruebas API separadas ejercitan además Spring, validación y H2. */
class ConcurrenciaYAislamientoTest {
 static final Instant T=Instant.parse("2026-11-29T05:00:00Z");
 static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
 static void reject(Runnable r) { try {r.run();}catch(IllegalArgumentException|com.pucp.paqrap.comun.error.ReglaNegocioException e){return;}throw new AssertionError("Debio rechazar"); }
 static void field(Object obj,String name,Object val){try{Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);f.set(obj,val);}catch(Exception e){throw new RuntimeException(e);}}
 @SuppressWarnings("unchecked") static <T>T proxy(Class<T> t, BiFunction<String,Object[],Object> fn){return (T)Proxy.newProxyInstance(t.getClassLoader(),new Class[]{t},(p,m,a)->{
  if(m.getName().equals("toString"))return "Fake<"+t.getSimpleName()+">";
  if(m.getName().equals("hashCode"))return System.identityHashCode(p);
  if(m.getName().equals("equals"))return p==a[0];
  Object v=fn.apply(m.getName(),a==null?new Object[0]:a);if(v!=null)return v;
  if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;if(m.getReturnType()==long.class)return 0L;
  if(m.getReturnType()==Optional.class)return Optional.empty();if(Collection.class.isAssignableFrom(m.getReturnType())||Iterable.class==m.getReturnType())return List.of();
  return null;
 });}
 static class ManualClock extends Clock { Instant now=T;public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;} }
 static class Env {
  Map<String,OrderEntity> orders=new LinkedHashMap<>(); Map<String,VehicleEntity> vehicles=new LinkedHashMap<>();
  Map<Long,ScenarioExecutionEntity> runs=new LinkedHashMap<>();Map<Long,ScenarioResultEntity> results=new LinkedHashMap<>();
  List<OrderStatusHistoryEntity> histories=new ArrayList<>(); List<RoadBlockEntity> blocks=new ArrayList<>();
  Map<Long,PlanResponse> plans = new HashMap<>();
  List<PlanificadorPort.SolicitudCiclo> requests=new ArrayList<>();List<Long> finalized=new ArrayList<>();
  ManualClock clock=new ManualClock(); WarehouseEntity central=new WarehouseEntity("CENTRAL",WarehouseKind.CENTRAL,27,14,null);
  OrderRepository orderRepo=proxy(OrderRepository.class,this::orderCall);
  VehicleRepository vehicleRepo=proxy(VehicleRepository.class,(m,a)->switch(m){case "findAll"->new ArrayList<>(vehicles.values());case "findById","getReferenceById"->Optional.ofNullable(vehicles.get(a[0]));case "save"->{var v=(VehicleEntity)a[0];vehicles.put(v.getVehicleId(),v);yield v;}default->null;});
  WarehouseRepository warehouseRepo=proxy(WarehouseRepository.class,(m,a)->switch(m){case "findAll"->List.of(central);case "findById","findByIdForUpdate"->Optional.of(central);default->null;});
  OrderStatusHistoryRepository historyRepo=proxy(OrderStatusHistoryRepository.class,(m,a)->switch(m){case "save"->{histories.add((OrderStatusHistoryEntity)a[0]);yield a[0];}case "saveAll"->{for(Object o:(Iterable<?>)a[0])histories.add((OrderStatusHistoryEntity)o);yield a[0];}case "findByOrderIdOrderByChangedAtAscStatusHistoryIdAsc"->histories.stream().filter(h->h.getOrderId().equals(a[0])).toList();default->null;});
  ScenarioExecutionRepository runRepo=proxy(ScenarioExecutionRepository.class,(m,a)->switch(m){case "findById"->Optional.ofNullable(runs.get(a[0]));case "findByStatusIn"->runs.values().stream().filter(e->((Collection<?>)a[0]).contains(e.getStatus())).toList();case "save"->a[0];default->null;});
  ScenarioResultRepository resultRepo=proxy(ScenarioResultRepository.class,(m,a)->switch(m){case "existsById"->results.containsKey(a[0]);case "save"->{var r=(ScenarioResultEntity)a[0];results.put(r.getExecution().getExecutionId(),r);yield r;}case "findById"->Optional.ofNullable(results.get(a[0]));default->null;});
  VehicleTypeParametersRepository typeRepo=proxy(VehicleTypeParametersRepository.class,(m,a)->m.equals("findAll")?List.of(new VehicleTypeParametersEntity(VehicleType.CAR,24,new BigDecimal("20"),new BigDecimal("8"))):null);
  BreakdownEventRepository breakdownRepo=proxy(BreakdownEventRepository.class,(m,a)->null);
  RoadBlockRepository blockRepo=proxy(RoadBlockRepository.class,(m,a)->switch(m){case "saveAll"->{for(Object o:(Iterable<?>)a[0])blocks.add((RoadBlockEntity)o);yield a[0];}case "borrarQueEmpiezanEntre"->{int prev=blocks.size();blocks.removeIf(b->!b.getStartsAt().isBefore((Instant)a[0])&&b.getStartsAt().isBefore((Instant)a[1]));yield prev-blocks.size();}default->null;});
  InventoryMovementRepository movementRepo=proxy(InventoryMovementRepository.class,(m,a)->m.equals("save")?a[0]:null);
  EstadoOperativoEscenarios state=new EstadoOperativoEscenarios(orderRepo,vehicleRepo,warehouseRepo,historyRepo);
  InventarioService inventory=new InventarioService(warehouseRepo,movementRepo);
  EjecutorPlanService executor=new EjecutorPlanService(id->Optional.ofNullable(plans.get(id)),orderRepo,historyRepo,vehicleRepo,warehouseRepo,breakdownRepo,inventory);
  DetectorColapso detector=new DetectorColapso(orderRepo,vehicleRepo,typeRepo,warehouseRepo);
  EventosSimulacion events=new EventosSimulacion(inventory,blockRepo,breakdownRepo,orderRepo);
  PlanificadorPort planner=new PlanificadorPort(){ public ResultadoCiclo planificar(SolicitudCiclo s){requests.add(s);return ResultadoCiclo.SIN_COLAPSO;}public Optional<ReplanificacionService.MetricasPlanificacion> metricas(long id){return Optional.of(new ReplanificacionService.MetricasPlanificacion(0,(int)Math.max(0,requests.stream().filter(s->s.ejecucionId()==id).count()-1)));}public void finalizar(long id){finalized.add(id);} };
  ScenarioResultService resultService=new ScenarioResultService(resultRepo,runRepo,proxy(EntityManager.class,(m,a)->m.equals("getReference")?runs.get(a[1]):null));
  MotorEscenarios motor=new MotorEscenarios(runRepo,proxy(PlatformTransactionManager.class,(m,a)->null),clock,
    new EscenariosProperties(Duration.ZERO,160,1440,Duration.ofHours(1)),proxy(ObjectProvider.class,(m,a)->planner),events,executor,detector,orderRepo,resultService);
  PedidoService pedidos=new PedidoService(orderRepo,historyRepo,event->{if(event instanceof PedidoRegistrado p)motor.alRegistrarPedido(p);},runRepo,motor);
  BloqueoService bloqueos=new BloqueoService(blockRepo);
  Env(){vehicles.put("TA01",new VehicleEntity("TA01",VehicleType.CAR,VehicleOperationalStatus.AVAILABLE));for(Object o:List.of(motor,executor,detector,events,pedidos))field(o,"estadoOperativo",state);}
  void addRun(long id,ScenarioType type){var e=new ScenarioExecutionEntity(type,T);field(e,"executionId",id);runs.put(id,e);}
  void addOrder(String id,Instant date,int hours){orders.put(id,new OrderEntity(id,"c1",new Location(27,14),2,date,DeliveryType.segunPlazo(hours),hours));}
  void advance(long seconds){clock.now=clock.now.plusSeconds(seconds);motor.tick();}
  Object orderCall(String m,Object[] a){
   var sorted=orders.values().stream().sorted(Comparator.comparing(OrderEntity::getRegisteredAt).thenComparing(OrderEntity::getOrderId)).toList();
   switch(m){
    case "findAll":return sorted;case "count":return (long)orders.size();case "findById":return Optional.ofNullable(orders.get(a[0]));
    case "save":{var o=(OrderEntity)a[0];orders.put(o.getOrderId(),o);return o;}
    case "saveAll":for(Object obj:(Iterable<?>)a[0]){OrderEntity o=(OrderEntity)obj;orders.put(o.getOrderId(),o);}return a[0];
    case "idsConPrefijo":return sorted.stream().map(OrderEntity::getOrderId).filter(id->id.startsWith(((String)a[0]).replace("%",""))).toList();
    case "existsByOrderIdStartingWith":return sorted.stream().anyMatch(o->o.getOrderId().startsWith((String)a[0]));
    case "catalogoDelPeriodo":return sorted.stream().filter(o->!o.getRegisteredAt().isBefore((Instant)a[0])&&(a[1]==null||o.getRegisteredAt().isBefore((Instant)a[1]))).toList();
    case "catalogoRegistradoEntre":return sorted.stream().filter(o->o.getRegisteredAt().isAfter((Instant)a[0])&&!o.getRegisteredAt().isAfter((Instant)a[1])).toList();
    case "idsRegistradosEn":return sorted.stream().filter(o->!o.getOrderId().startsWith("SIM-")&&o.getRegisteredAt().equals(a[0])).map(OrderEntity::getOrderId).toList();
    case "idsRegistradosEntre":return sorted.stream().filter(o->!o.getOrderId().startsWith("SIM-")&&o.getRegisteredAt().isAfter((Instant)a[0])&&!o.getRegisteredAt().isAfter((Instant)a[1])).map(OrderEntity::getOrderId).toList();
    case "reiniciarDesde":return 0;
    case "registradosEnDesde":return sorted.stream().filter(o->!o.getOrderId().startsWith("SIM-")&&!o.getRegisteredAt().isBefore((Instant)a[0])&&!o.getRegisteredAt().isAfter((Instant)a[1])&&o.getDeadline().isAfter((Instant)a[1])&&o.getStatus()==OrderStatus.REGISTERED).skip(((Pageable)a[2]).getOffset()).limit(((Pageable)a[2]).getPageSize()).toList();
    case "incumplidosDesde":return sorted.stream().filter(o->!o.getOrderId().startsWith("SIM-")&&!o.getRegisteredAt().isBefore((Instant)a[0])&&!o.getDeadline().isAfter((Instant)a[1])&&(o.getStatus()!=OrderStatus.DELIVERED||o.getDeliveredAt().isAfter(o.getDeadline()))).sorted(Comparator.comparing(OrderEntity::getDeadline)).limit(((Pageable)a[2]).getPageSize()).toList();
    case "countByRegisteredAtGreaterThanEqualAndRegisteredAtLessThanEqual":return sorted.stream().filter(o->!o.getOrderId().startsWith("SIM-")&&!o.getRegisteredAt().isBefore((Instant)a[0])&&!o.getRegisteredAt().isAfter((Instant)a[1])).count();
    case "contarPendientesEnDesde":return sorted.stream().filter(o->!o.getOrderId().startsWith("SIM-")&&!o.getRegisteredAt().isBefore((Instant)a[0])&&!o.getRegisteredAt().isAfter((Instant)a[1])&&o.getDeadline().isAfter((Instant)a[1])&&o.getStatus()!=OrderStatus.DELIVERED).count();
    case "contarVencidosSinEntregar":return sorted.stream().filter(o->!o.getOrderId().startsWith("SIM-")&&!o.getRegisteredAt().isBefore((Instant)a[0])&&!o.getDeadline().isAfter((Instant)a[1])&&o.getStatus()!=OrderStatus.DELIVERED).count();
   }return null;
  }
 }
 // DIA y 5D simultaneos; relojes independientes
 @Test
 void regresion01() {
  Env e=new Env();e.addRun(1,ScenarioType.DAY_TO_DAY);e.addRun(2,ScenarioType.FIVE_DAY);e.motor.iniciar(1);e.motor.iniciar(2);e.advance(5);check(e.motor.instanteSimulado(1).orElseThrow().equals(T.plusSeconds(5)),"reloj real");check(e.motor.instanteSimulado(2).orElseThrow().equals(T.plusSeconds(800)),"reloj sim");
 }

 // Orden inverso; restricciones por grupo incluido pausado
 @Test
 void regresion02() {
  Env e=new Env();e.addRun(1,ScenarioType.FIVE_DAY);e.addRun(2,ScenarioType.DAY_TO_DAY);e.addRun(3,ScenarioType.COLLAPSE);e.addRun(4,ScenarioType.DAY_TO_DAY);e.motor.iniciar(1);e.motor.iniciar(2);e.motor.pausar(1);reject(()->e.motor.iniciar(3));reject(()->e.motor.iniciar(4));
 }

 // Pausar/detener simulacion no pausa/detiene Dia a Dia
 @Test
 void regresion03() {
  Env e=new Env();e.addRun(1,ScenarioType.DAY_TO_DAY);e.addRun(2,ScenarioType.FIVE_DAY);e.motor.iniciar(1);e.motor.iniciar(2);e.motor.pausar(2);e.advance(10);check(e.motor.instanteSimulado(2).orElseThrow().equals(T),"pausa");e.motor.detener(2);check(e.runs.get(1L).getStatus()==ScenarioStatus.RUNNING,"real vivo");check(e.results.containsKey(2L),"resultado");
 }

 // Pedidos/entregas/historial/vehiculos no se contaminan
 @Test
 void regresion04() {
  Env e=new Env();e.addOrder("P1",T,4);e.vehicles.get("TA01").moverA(40,20,3);e.state.iniciar(2,ScenarioType.FIVE_DAY,T);e.state.pedido(2,"P1").orElseThrow().entregar(T.plusSeconds(60));e.state.vehiculo(2,"TA01").orElseThrow().moverA(60,30,7);e.state.guardarHistorial(2,e.state.pedido(2,"P1").orElseThrow(),OrderStatus.DELIVERED,T.plusSeconds(60),"TA01");check(e.orders.get("P1").getStatus()==OrderStatus.REGISTERED,"pedido real");check(e.vehicles.get("TA01").getCurrentX()==40,"pos real");check(e.histories.isEmpty(),"hist real");check(e.state.historial(2,"P1").size()==2,"hist virtual");
 }

 // Arranque de 5D no reinicia estado de Dia a Dia
 @Test
 void regresion05() {
  Env e=new Env();e.addOrder("P1",T,4);e.addRun(1,ScenarioType.DAY_TO_DAY);e.motor.iniciar(1);e.orders.get("P1").entregar(T.plusSeconds(1));e.vehicles.get("TA01").moverA(35,18,5);e.addRun(2,ScenarioType.FIVE_DAY);e.motor.iniciar(2);check(e.orders.get("P1").getStatus()==OrderStatus.DELIVERED,"entrega real no borrada");check(e.vehicles.get("TA01").getCurrentX()==35,"pos real no borrada");check(e.state.pedido(2,"P1").orElseThrow().getStatus()==OrderStatus.REGISTERED,"input sim independiente");
 }

 // 5D colapsa y persiste resultado; Dia a Dia sigue
 @Test
 void regresion06() {
  Env e=new Env();e.addOrder("P1",T,4);e.addRun(1,ScenarioType.DAY_TO_DAY);e.addRun(2,ScenarioType.FIVE_DAY);e.motor.iniciar(1);e.motor.iniciar(2);e.advance(120);check(e.runs.get(2L).getStatus()==ScenarioStatus.COLLAPSED,"5D collapsed");check(e.results.containsKey(2L),"resultado sim");check(e.runs.get(1L).getStatus()==ScenarioStatus.RUNNING,"real sigue");check(e.finalized.equals(List.of(2L)),"solo finaliza sim");
 }

 // Dia a Dia alerta por colapso pero sigue ejecutandose
 @Test
 void regresion07() {
  Env e=new Env();e.addOrder("P1",T,4);e.addRun(1,ScenarioType.DAY_TO_DAY);e.motor.iniciar(1);e.advance(4*3600+1);check(e.detector.colapso(1).isPresent(),"alerta");check(e.runs.get(1L).getStatus()==ScenarioStatus.RUNNING,"real running");
 }

 // No adelanta pedidos futuros al planificador
 @Test
 void regresion08() {
  Env e=new Env();e.addOrder("P1",T.plusSeconds(3600),4);e.state.iniciar(2,ScenarioType.FIVE_DAY,T);check(e.state.registrados(2,T,T,PageRequest.of(0,100)).isEmpty(),"sin conocimiento futuro");check(e.state.registrados(2,T,T.plusSeconds(3600),PageRequest.of(0,100)).size()==1,"a su hora");
 }

 // Carga doble cruzando anio + deduplicacion
 @Test
 void regresion09() {
  Env e=new Env();var files=List.of(new PedidoService.ArchivoMensual("ventas.202612.txt","31d23h30m:27,14,c1,2,36\n"),new PedidoService.ArchivoMensual("ventas.202701.txt","01d00h15m:28,14,c2,3,04\n"));check(e.pedidos.cargarMultiples(files).registrados()==2,"dos guardados");check(e.orders.get("202612-00001").getRegisteredAt().equals(Instant.parse("2027-01-01T04:30:00Z")),"lima");check(e.pedidos.cargarMultiples(files).omitidos()==2,"sin duplicacion");
 }

 // Segundo archivo invalido no guarda el primero
 @Test
 void regresion10() {
  Env e=new Env();reject(()->e.pedidos.cargarMultiples(List.of(new PedidoService.ArchivoMensual("ventas.202611.txt","01d00h00m:27,14,c1,2,36\n"),new PedidoService.ArchivoMensual("ventas.202612.txt","mal\n"))));check(e.orders.isEmpty(),"atomicidad prevalidacion");
 }

 // Rechazo mes repetido o mas de dos archivos
 @Test
 void regresion11() {
  Env e=new Env();var f=new PedidoService.ArchivoMensual("ventas.202611.txt","01d00h00m:27,14,c1,2,36\n");reject(()->e.pedidos.cargarMultiples(List.of(f,f)));reject(()->e.pedidos.cargarMultiples(List.of(f,f,f)));
 }

 // Carga del mes siguiente entra al escenario correcto
 @Test
 void regresion12() {
  Env e=new Env();e.state.iniciar(2,ScenarioType.FIVE_DAY,T);e.pedidos.cargarMultiples(List.of(new PedidoService.ArchivoMensual("ventas.202611.txt","30d23h30m:27,14,c1,2,36\n"),new PedidoService.ArchivoMensual("ventas.202612.txt","01d00h15m:28,14,c2,3,04\n")));check(e.state.catalogo(2).size()==2,"incluye dos meses");check(e.state.idsEntre(2,T,Instant.parse("2026-12-01T05:30:00Z")).size()==2,"eventos dos meses");
 }

 // Vista cerrada no recibe nuevas importaciones
 @Test
 void regresion13() {
  Env e=new Env();e.state.iniciar(2,ScenarioType.FIVE_DAY,T);e.state.finalizar(2);e.addOrder("P1",T.plusSeconds(60),4);e.state.incorporarAlConfirmar(null,List.of(e.orders.get("P1")));check(e.state.catalogo(2).isEmpty(),"cerrada");
 }

 // Pedidos manuales sim no aparecen en otra simulacion
 @Test
 void regresion14() {
  Env e=new Env();e.addOrder("SIM-2-x",T,4);e.state.iniciar(2,ScenarioType.FIVE_DAY,T);e.state.iniciar(3,ScenarioType.FIVE_DAY,T);check(e.state.catalogo(2).size()==1,"own");check(e.state.catalogo(3).isEmpty(),"not other");
 }

 // Dos archivos de bloqueos no borran el primer mes
 @Test
 void regresion15() {
  Env e=new Env();var r=e.bloqueos.cargarMultiples(List.of(new BloqueoService.ArchivoMensual("bloqueo.2611.txt","30d01h00m-30d02h00m:10,10,12,10\n"),new BloqueoService.ArchivoMensual("bloqueo.2612.txt","01d01h00m-01d02h00m:20,10,22,10\n")));check(r.registrados()==2&&e.blocks.size()==2,"2 meses bloqueos");
 }

 /** La entrega virtual ejecutada realmente no cambia pedidos ni carga de la operación real. */
 @Test
 void ejecucionDeEntregaSoloModificaLaSimulacion() {
  Env e = new Env();
  e.addOrder("P1", T, 4);
  e.state.iniciar(2, ScenarioType.FIVE_DAY, T);
  e.executor.prepararInicio(2, T);
  RutaResponse ruta = new RutaResponse("R1", "TA01", "CAR", T, T.plusSeconds(120), 3.0, 24.0,
    List.of(new ParadaResponse(1, ParadaResponse.ENTREGA, "P1", null, 30, 14, 2,
      T.plusSeconds(60), T.plusSeconds(120), 2, 0)));
  e.plans.put(2L, new PlanResponse(ModoOperacion.SIMULACION_5D, T, true, false, 24, 3,
    1, 1, 1500, false, List.of(ruta), List.of(), List.of()));
  e.executor.avanzar(2, T, T.plusSeconds(90));
  check(e.state.pedido(2, "P1").orElseThrow().getStatus() == OrderStatus.DELIVERED, "entrega virtual");
  check(e.orders.get("P1").getStatus() == OrderStatus.REGISTERED, "pedido real intacto");
  check(e.vehicles.get("TA01").getCurrentX() == 27, "posición real intacta");
  check(e.executor.metricas(2).orElseThrow().pedidosEntregados() == 1, "métrica virtual");
 }

 @Test
 void registroManualPrefiereDiaADiaYPermiteElegirSimulacion() {
  Env e = new Env();
  e.addRun(1, ScenarioType.DAY_TO_DAY);
  e.addRun(2, ScenarioType.FIVE_DAY);
  e.motor.iniciar(1);
  e.motor.iniciar(2);
  var request = new RegistrarPedidoRequest("c1", 27, 14, 2, DeliveryType.PRIORITY, 4, null);
  var real = e.pedidos.registrar(request);
  var virtual = e.pedidos.registrar(request, 2L);
  check(real.id().startsWith("M-"), "por defecto usa Día a Día");
  check(virtual.id().startsWith("SIM-2-"), "id de simulación");
  check(e.state.pedido(2, real.id()).isEmpty(), "pedido manual real no invade la simulación");
  check(e.state.pedido(2, virtual.id()).isPresent(), "pedido manual virtual disponible");
  e.state.idsEntre(2, T.minusSeconds(1), T.plusSeconds(1));
  check(e.state.pedido(2, real.id()).isEmpty(), "ni por actualización del catálogo");
 }

 @Test
 void archivoBloqueosInvalidoNoReemplazaMesAnterior() {
  Env e = new Env();
  var noviembre = new BloqueoService.ArchivoMensual("bloqueo.2611.txt", "30d01h00m-30d02h00m:10,10,12,10\n");
  e.bloqueos.cargarMultiples(List.of(noviembre));
  reject(() -> e.bloqueos.cargarMultiples(List.of(noviembre,
    new BloqueoService.ArchivoMensual("bloqueo.2612.txt", "linea invalida\n"))));
  check(e.blocks.size() == 1, "prevalidación no borra bloqueos ya cargados");
 }
}
