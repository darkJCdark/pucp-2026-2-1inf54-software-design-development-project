package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.edu.pucp.paqrap.planner.search.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** Executable regression checks, available without Maven/JUnit: --self-test. */
public final class VerificationMain {
    private static int checks;
    public static void main(String[] args)throws Exception {runAll();}
    public static void runAll()throws Exception{
        checks=0;
        Properties props=new Properties();props.setProperty("grasp.iterations","2");props.setProperty("sa.maximumIterations","30");props.setProperty("sa.maximumWithoutImprovement","30");
        props.setProperty("fleet.cars","2");props.setProperty("fleet.motorcycles","1");props.setProperty("fleet.bicycles","1");
        ExperimentConfig c=new ExperimentConfig(props,Path.of(""));
        ScenarioSpec spec=new ScenarioSpec("TEST","NORMAL","SYNTHETIC",2,901,LocalDate.of(2026,9,9),7,8);
        ProblemInstance p=InstanceFactory.create(spec,c);
        check(p.sha256().equals(InstanceFactory.create(spec,c).sha256()),"Identical input fingerprint");
        check(p.central().location().equals(new Location(27,14)),"Latest source central coordinates preserved");
        var keys=new ArrayList<>(p.snapshot().vehiclesById().keySet());var sorted=new ArrayList<>(keys);Collections.sort(sorted);
        check(keys.equals(sorted),"Deterministic vehicle iteration order");
        try {p.snapshot().vehiclesById().clear();throw new AssertionError("Mutable snapshot");}catch(UnsupportedOperationException expected){check(true,"Snapshot map is immutable");}
        var emptyAudit=CommonAudit.evaluate(p,OperationalPlan.empty());
        check(!emptyAudit.fullFeasible()&&emptyAudit.missingOrders()==2,"Empty cheap plan is not complete feasible");
        check(emptyAudit.routeValid(),"Route validity is distinguished from demand coverage");
        for(UnifiedPlanner solver:List.of(new GraspAdapter(),new SaAdapter())){
            var first=solver.solve(p,c,42);var second=solver.solve(p,c,42);
            var a=CommonAudit.evaluate(p,first.plan());var b=CommonAudit.evaluate(p,second.plan());
            check(a.fullFeasible(),solver.name()+" creates a complete feasible plan for the test fixture");
            check(Json.encode(a.details().get("routes")).equals(Json.encode(b.details().get("routes"))),solver.name()+" is repeatable with a fixed iteration protocol");
            check(p.snapshot().inventory().availableStock("NORTH_WEST")==1000,solver.name()+" does not modify input stock");
        }
        check(p.sha256().equals(InstanceFactory.create(spec,c).sha256()),"Input identity remains unchanged after both solvers");
        SearchControl control=SearchControl.install(1);boolean stopped=false;
        try(control){Thread.sleep(5);SearchControl.checkpoint();}catch(SearchStopped expected){stopped=true;}
        check(stopped&&control.timedOut(),"Cooperative budget stops without a business-rule exception");
        Properties noFleet=new Properties();noFleet.putAll(props);for(String t:List.of("cars","motorcycles","bicycles"))noFleet.setProperty("fleet."+t,"0");
        ExperimentConfig zero=new ExperimentConfig(noFleet,Path.of(""));ProblemInstance z=InstanceFactory.create(spec,zero);
        AlgorithmOutput failure=new SaAdapter().solve(z,zero,1);
        check(failure.initialization().equals("NO_SOLUTION"),"SA initialization failure is explicit and not called proven infeasibility");
        check(!CommonAudit.evaluate(z,failure.plan()).fullFeasible(),"Failed initializer does not score as a zero-cost success");
        ScenarioSpec real=new ScenarioSpec("REAL_TEST","REAL","REAL",0,0,LocalDate.of(2026,9,9),7,8);
        ProblemInstance r=InstanceFactory.create(real,c);
        check(!r.orders().isEmpty()&&r.orders().stream().noneMatch(o->o.registeredAt().isAfter(r.snapshot().planningTime())),"Real batch includes only already registered orders");
        check(r.blocks().stream().anyMatch(bk->bk.startsAt().isAfter(r.snapshot().planningTime())),"Future relevant road blocks are not truncated to the arrival window");
        Properties splitProps=new Properties();splitProps.putAll(props);splitProps.setProperty("grasp.iterations","3");
        ExperimentConfig splitConfig=new ExperimentConfig(splitProps,Path.of(""));
        ProblemInstance split=InstanceFactory.create(new ScenarioSpec("SPLIT_TEST","SPLIT","SYNTHETIC",3,903,LocalDate.of(2026,9,9),7,8),splitConfig);
        int largest=split.orders().stream().mapToInt(Order::packages).max().orElse(0);
        check(largest>VehicleType.CAR.capacity() && CommonAudit.evaluate(split,new GraspAdapter().solve(split,splitConfig,42).plan()).fullFeasible(),
                "GRASP divides an order larger than any vehicle into partial deliveries (P&R 13)");
        ProblemInstance longWindow=InstanceFactory.create(new ScenarioSpec("REAL_LONG","REAL","REAL",0,0,LocalDate.of(2026,9,13),7,15),c);
        check(longWindow.manifest().containsKey("orders_expired_at_planning") && !longWindow.manifest().containsKey("orders_expired_before_planning_excluded"),
                "Expired orders remain in default full-demand batches and are recorded");
        Properties exclProps=new Properties();exclProps.putAll(props);exclProps.setProperty("orders.expiredPolicy","EXCLUDE");
        ProblemInstance excluded=InstanceFactory.create(longWindow.spec(),new ExperimentConfig(exclProps,Path.of("")));
        check(excluded.orders().stream().noneMatch(o->o.deadline().isBefore(excluded.snapshot().planningTime())) && excluded.manifest().containsKey("orders_expired_before_planning_excluded"),"Explicit EXCLUDE sensitivity policy records exclusions");
        Instant at=p.snapshot().planningTime();
        Order impossible=new Order("IMPOSSIBLE",new Location(65,48),3,at,at.plusSeconds(600));
        Order easy=new Order("EASY",new Location(28,15),3,at,at.plusSeconds(8*3600));
        ProblemInstance certificate=new ProblemInstance(p.spec(),p.snapshot(),List.of(impossible,easy),p.blocks(),p.manifest(),p.sha256());
        check(CommonAudit.provablyUnservable(certificate).equals(Set.of("IMPOSSIBLE")),
                "Optimistic travel bound rules out the impossible fixture, not the easy one");
        var resources=Collections.list(VerificationMain.class.getClassLoader().getResources("pe/edu/pucp/paqrap/planner/route/OperationalPlanEvaluator.class"));
        check(resources.size()==1,"Exactly one shared evaluator on the runtime classpath");
        NumericalRegressionChecks.run(VerificationMain::check);
        System.out.println("PASS: "+checks+" experimental regression checks.");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+(++checks)+": "+message);}
}
