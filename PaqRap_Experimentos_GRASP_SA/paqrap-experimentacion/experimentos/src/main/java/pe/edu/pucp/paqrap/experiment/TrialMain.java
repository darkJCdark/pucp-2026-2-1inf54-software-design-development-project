package pe.edu.pucp.paqrap.experiment;

import pe.edu.pucp.paqrap.planner.search.SearchControl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static pe.edu.pucp.paqrap.experiment.Json.obj;

/** One child JVM per measured run. Startup, input IO, warm-up and final audit are not search time. */
public final class TrialMain {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Internal worker: expected job.properties");
        Path jobFile=Path.of(args[0]);Properties job=new Properties();
        try(var r=Files.newBufferedReader(jobFile,StandardCharsets.UTF_8)){job.load(r);}
        Path root=Path.of(job.getProperty("root"));
        ExperimentConfig cfg=ExperimentConfig.read(Path.of(job.getProperty("config")),root);cfg.validate();
        ScenarioSpec spec=ScenarioSpec.parse(job.getProperty("scenario"));
        String alg=job.getProperty("algorithm"),runId=job.getProperty("run_id");
        long seed=Long.parseLong(job.getProperty("seed")),requestedBudget=Long.parseLong(job.getProperty("budget"));
        Path prefix=Path.of(job.getProperty("prefix"));
        Map<String,String> row=new LinkedHashMap<>();
        put(row,"run_id",runId,"instance_id",spec.id(),"family",spec.family(),"source",spec.source(),"instance_seed",spec.instanceSeed(),"search_seed",seed,"repetition",job.getProperty("repetition"),"algorithm",alg,"mode",cfg.text("mode","TIME"),"budget_ms",cfg.effectiveBudget(requestedBudget),"config_sha256",Json.sha256(Json.encode(cfg.asMap())));
        try{
            ProblemInstance p=InstanceFactory.create(spec,cfg);
            if(!p.sha256().equals(job.getProperty("expected_input_sha256")))throw new IllegalStateException("Input fingerprint differs from controller");
            put(row,"input_sha256",p.sha256(),"orders_total",p.orders().size());
            warmup(cfg);
            System.gc(); // outside measured section, same protocol for both child JVMs
            UnifiedPlanner solver=alg.equals("GRASP")?new GraspAdapter():alg.equals("SA")?new SaAdapter():null;
            if(solver==null)throw new IllegalArgumentException("Unknown algorithm: "+alg);
            AlgorithmOutput output;
            SearchControl control=SearchControl.install(cfg.effectiveBudget(requestedBudget));
            try(control){output=solver.solve(p,cfg,seed);}
            long auditStart=System.nanoTime();
            CommonAudit.AuditResult audit=CommonAudit.evaluate(p,output.plan());
            double auditMs=(System.nanoTime()-auditStart)/1e6;
            String status=audit.fullFeasible()?"OK":
                    output.initialization().equals("FAILED")?"NO_INITIAL_PLAN":
                    output.plan().routes().isEmpty()&&control.timedOut()?"TIME_LIMIT_NO_PLAN":
                    audit.routeValid()?"PARTIAL":"INVALID";
            put(row,"status",status,"termination",control.timedOut()?"TIME_LIMIT":output.termination(),"initialization",output.initialization(),
                    "full_feasible",audit.fullFeasible(),"route_constraints_valid",audit.routeValid(),"orders_fully_served",audit.completeOrders(),
                    "orders_missing",audit.missingOrders(),"packages_total",audit.totalPackages(),"packages_covered_on_time",audit.coveredPackages(),
                    "coverage_orders_pct",Csv.number(p.orders().isEmpty()?100:100.0*audit.completeOrders()/p.orders().size()),
                    "orders_provably_unservable",audit.provablyUnservable(),"servable_orders_fully_served",audit.servableComplete(),
                    "coverage_servable_pct",Csv.number(p.orders().size()==audit.provablyUnservable()?100:100.0*audit.servableComplete()/(p.orders().size()-audit.provablyUnservable())),
                    "full_servable_feasible",audit.fullServableFeasible(),"algorithm_version",solver.version(),
                    "cost_complete_feasible",audit.fullFeasible()?Csv.number(audit.rawCost()):"", "cost_raw_do_not_rank_incomplete",Csv.number(audit.rawCost()),
                    "distance_km",Csv.number(audit.distanceKm()),"routes",audit.routes(),"elapsed_ms",Csv.number(control.elapsedMs()),
                    "time_first_complete_ms",Csv.number(control.firstCompleteMs()),"initialization_ms",Csv.number(output.initializationMs()),"final_audit_ms",Csv.number(auditMs),
                    "iterations",control.iterations(),"neighbor_attempts",control.attempts(),"invalid_neighbors",control.invalid(),"accepted_neighbors",control.accepted(),
                    "plan_evaluations",control.evaluations(),"route_schedules",control.schedules(),"path_queries",control.paths(),"heap_sampled_peak_mib",Csv.number(control.sampledHeapBytes()/1048576.0),
                    "detail",output.detail(),"plan_sha256",Json.sha256(Json.encode(audit.details().get("routes"))));
            Files.writeString(Path.of(prefix+".plan.json"),Json.encode(obj("run",row,"audit",audit.details())),StandardCharsets.UTF_8);
            StringBuilder trace=new StringBuilder("elapsed_ms,unserved_orders,planned_cost\n");
            for(var sample:control.trace())trace.append(Csv.number(sample.elapsedMs())).append(',').append(sample.missing()).append(',').append(Csv.number(sample.cost())).append('\n');
            Files.writeString(Path.of(prefix+".trace.csv"),trace.toString(),StandardCharsets.UTF_8);
        }catch(Exception e){
            e.printStackTrace();put(row,"status","ERROR","full_feasible",false,"detail",e.getClass().getSimpleName()+": "+e.getMessage());
        }
        Properties result=new Properties();result.putAll(row);
        try(var writer=Files.newBufferedWriter(Path.of(prefix+".result.properties"),StandardCharsets.UTF_8)){result.store(writer,"One measured trial; inspect log on ERROR");}
        Files.writeString(Path.of(prefix+".csv"),String.join(",",Csv.COLUMNS)+"\n"+Csv.row(row)+"\n",StandardCharsets.UTF_8);
    }
    private static void warmup(ExperimentConfig config){
        int runs=config.integer("warmup.rounds",1);
        Properties p=config.copy();p.setProperty("grasp.iterations","1");p.setProperty("sa.maximumIterations","15");p.setProperty("sa.maximumWithoutImprovement","15");
        p.setProperty("fleet.cars","2");p.setProperty("fleet.motorcycles","1");p.setProperty("fleet.bicycles","1");
        ExperimentConfig small=new ExperimentConfig(p,config.root());
        ScenarioSpec s=new ScenarioSpec("WARMUP","NORMAL","SYNTHETIC",2,707,LocalDate.of(2026,9,9),7,8);
        for(int i=0;i<runs;i++){
            ProblemInstance instance=InstanceFactory.create(s,small);
            new GraspAdapter().solve(instance,small,123);
            new SaAdapter().solve(instance,small,123);
        }
    }
    static void put(Map<String,String> m,Object...pairs){for(int i=0;i<pairs.length;i+=2)m.put(pairs[i].toString(),String.valueOf(pairs[i+1]));}
}
