package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.pucp.paqrap.modelo.CargadorPedidos;
import pe.pucp.paqrap.modelo.CargadorBloqueos;
import pe.pucp.paqrap.modelo.CargadorMantenimiento;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static pe.edu.pucp.paqrap.experiment.Json.obj;

/** Materializes identical, immutable inputs before either algorithm is measured. */
public final class InstanceFactory {
    private InstanceFactory(){}
    public static ProblemInstance create(ScenarioSpec spec,ExperimentConfig config) {
        ZoneId zone=ShiftSchedule.DEFAULT_ZONE;
        Instant from=spec.date().atTime(spec.fromHour(),0).atZone(zone).toInstant();
        Instant to=spec.date().atTime(spec.toHour(),0).atZone(zone).toInstant();
        // REAL is a batch of already received orders, not an anticipative online simulation.
        Instant planning=spec.source().equals("REAL")?to:from;
        List<Warehouse> warehouses=List.of(
                Warehouse.central("CENTRAL",new Location(config.integer("central.x",27),config.integer("central.y",14))),
                Warehouse.intermediate("NORTH_WEST",new Location(12,38),config.integer("stock.northwest",1000)),
                Warehouse.intermediate("EAST",new Location(config.integer("east.x",57),config.integer("east.y",27)),config.integer("stock.east",1000)));
        Warehouse central=warehouses.getFirst();
        List<Order> orders=new ArrayList<>();List<RoadBlock> blocks=new ArrayList<>();
        List<MaintenanceDay> maintenance=new ArrayList<>();List<BreakdownEvent> breakdowns=new ArrayList<>();
        Map<String,Object> inputFiles=new TreeMap<>();
        Random generator=new Random(spec.instanceSeed());
        if(spec.source().equals("REAL")) {
            YearMonth month=YearMonth.from(spec.date());
            String ym=String.format("%04d%02d",month.getYear(),month.getMonthValue());
            Path sales=config.root().resolve("data/ventas/ventas."+ym+".txt");
            orders.addAll(CargadorPedidos.desdeArchivo(sales,month,zone,from,to));
            if(spec.orders()>0 && orders.size()>spec.orders()) orders=new ArrayList<>(orders.subList(0,spec.orders()));
            hashFile(inputFiles,sales,config.root());
            // Load all provided months overlapping the planning horizon, not only the order-arrival hour.
            Instant horizon=orders.stream().map(Order::deadline).max(Instant::compareTo).orElse(planning).plus(Duration.ofDays(2));
            YearMonth last=YearMonth.from(horizon.atZone(zone));
            for(YearMonth m=month;!m.isAfter(last);m=m.plusMonths(1)){
                Path file=config.root().resolve(String.format("data/bloqueos/bloqueo.%02d%02d.txt",m.getYear()%100,m.getMonthValue()));
                if(!Files.exists(file))throw new IllegalArgumentException("Missing road block data: "+file);
                blocks.addAll(CargadorBloqueos.desdeArchivo(file,m,zone,planning,horizon));hashFile(inputFiles,file,config.root());
            }
            Path file=config.root().resolve("data/mant.preventivo.09.10.txt");
            maintenance.addAll(CargadorMantenimiento.desdeArchivo(file));hashFile(inputFiles,file,config.root());
            if(orders.isEmpty())throw new IllegalArgumentException("No orders in real window: "+spec.id());
        } else {
            int[] deadlines={4,8,12,18,36};
            for(int i=0;i<spec.orders();i++){
                // Explicit synthetic design: all orders known at the snapshot; no claim of real data.
                int x=5+generator.nextInt(61),y=5+generator.nextInt(41);
                int quantity=1+generator.nextInt(8);
                if(spec.family().equals("SPLIT") && i%3==0)quantity=25+generator.nextInt(8);
                int hours=deadlines[generator.nextInt(deadlines.length)];
                orders.add(new Order(spec.id()+"-P"+String.format("%03d",i+1),new Location(x,y),quantity,planning,planning.plusSeconds(hours*3600L)));
            }
            if(spec.family().equals("BLOCKED")){
                for(int i=0;i<3;i++){
                    int x=30+generator.nextInt(20),y=10+generator.nextInt(25);
                    blocks.add(new RoadBlock(planning,planning.plus(Duration.ofHours(4+i)),List.of(new Location(x,y),new Location(x,y+4))));
                }
            }
        }
        Map<String,VehicleOperationalState> states=new LinkedHashMap<>();
        int[] amounts={config.integer("fleet.cars",10),config.integer("fleet.motorcycles",15),config.integer("fleet.bicycles",12)};
        for(VehicleType type:VehicleType.values()){
            int n=amounts[type.ordinal()];
            for(int i=1;i<=n;i++){
                Vehicle vehicle=new Vehicle(type.fleetCode()+String.format("%02d",i),type,true);
                states.put(vehicle.id(),new VehicleOperationalState(vehicle,VehicleStatus.AVAILABLE,central.location(),planning));
                // Controlled resource perturbation: same unavailable units for both algorithms.
                if(spec.family().equals("REDUCED") && i<=n/2)maintenance.add(new MaintenanceDay(vehicle.id(),spec.date()));
            }
        }
        FleetProfile fleet=FleetProfile.defaults()
                .withSpeed(VehicleType.CAR,config.decimal("speed.car",40))
                .withSpeed(VehicleType.MOTORCYCLE,config.decimal("speed.motorcycle",25))
                .withSpeed(VehicleType.BICYCLE,config.decimal("speed.bicycle",12));
        OperationalSnapshot snapshot=new OperationalSnapshot(planning,fleet,InventorySnapshot.from(warehouses),states,
                new MaintenanceCalendar(zone,maintenance),new ShiftSchedule(zone),breakdowns);
        orders.sort(Comparator.comparing(Order::registeredAt).thenComparing(Order::id));
        if(orders.stream().anyMatch(o->o.registeredAt().isAfter(planning)))throw new IllegalStateException("Snapshot cannot know future orders");
        Map<String,Object> manifest=obj("id",spec.id(),"family",spec.family(),"source",spec.source(),"instance_seed",spec.instanceSeed(),
                "planning_time",planning,"collection_from",from,"collection_to_exclusive",to,
                "model","snapshot-batch; all first departures from central; inherited domain semantics",
                "warehouses",warehouses.stream().map(w->obj("id",w.id(),"x",w.location().x(),"y",w.location().y(),"central",w.isCentral(),"initial_stock",w.initialStock(),"capacity",w.capacity())).toList(),
                "fleet",snapshot.vehiclesById().values().stream().map(v->{VehicleParameters p=fleet.parametersFor(v.vehicle().type());return obj("id",v.vehicle().id(),"type",v.vehicle().type(),"status",v.status(),"x",v.location().x(),"y",v.location().y(),"available_at",v.availableAt(),"capacity",p.capacity(),"speed",p.speedKmPerHour(),"cost_km",p.costPerKm());}).toList(),
                "orders",orders.stream().map(o->obj("id",o.id(),"x",o.destination().x(),"y",o.destination().y(),"packages",o.packages(),"registered_at",o.registeredAt(),"deadline",o.deadline())).toList(),
                "blocks",blocks.stream().map(b->obj("from",b.startsAt(),"to",b.endsAt(),"nodes",b.nodes().stream().map(n->List.of(n.x(),n.y())).toList())).toList(),
                "maintenance",maintenance.stream().map(m->obj("vehicle",m.vehicleId(),"date",m.date())).toList(),
                "breakdowns",List.of(),"source_file_sha256",inputFiles);
        return new ProblemInstance(spec,snapshot,orders,blocks,manifest,Json.sha256(Json.encode(manifest)));
    }
    private static void hashFile(Map<String,Object> m,Path f,Path root){try{m.put(root.relativize(f).toString().replace('\\','/'),Json.sha256(Files.readAllBytes(f)));}catch(Exception e){throw new IllegalArgumentException("Cannot hash "+f,e);}}
}
