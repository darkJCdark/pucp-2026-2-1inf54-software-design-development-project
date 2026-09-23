package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.domain.*;
import java.util.*;

public record ProblemInstance(ScenarioSpec spec,OperationalSnapshot snapshot,List<Order> orders,
                              List<RoadBlock> blocks,Map<String,Object> manifest,String sha256) {
    public ProblemInstance {orders=List.copyOf(orders);blocks=List.copyOf(blocks);manifest=Collections.unmodifiableMap(manifest);}
    public Warehouse central(){return snapshot.inventory().warehouses().stream().filter(Warehouse::isCentral).findFirst().orElseThrow();}
}
