package pe.edu.pucp.paqrap.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Immutable experiment settings, deliberately independent of Spring and the frontend. */
public final class ExperimentConfig {
    private final Properties values;
    private final Path root;
    public ExperimentConfig(Properties values, Path root) {
        this.values=new Properties(); this.values.putAll(values); this.root=root.toAbsolutePath().normalize();
    }
    public static ExperimentConfig read(Path file, Path root) throws IOException {
        Properties p=new Properties();
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(reader);}
        return new ExperimentConfig(p,root);
    }
    public String text(String key,String fallback){return values.getProperty(key,fallback).trim();}
    public int integer(String key,int fallback){return Integer.parseInt(text(key,Integer.toString(fallback)));}
    public long number(String key,long fallback){return Long.parseLong(text(key,Long.toString(fallback)));}
    public double decimal(String key,double fallback){return Double.parseDouble(text(key,Double.toString(fallback)));}
    public boolean flag(String key,boolean fallback){String v=text(key,Boolean.toString(fallback));if(!v.equalsIgnoreCase("true")&&!v.equalsIgnoreCase("false"))throw new IllegalArgumentException("Boolean expected: "+key);return Boolean.parseBoolean(v);}
    public Path path(String key,String fallback){return root.resolve(text(key,fallback)).normalize();}
    public Path root(){return root;}
    public Properties copy(){Properties p=new Properties();p.putAll(values);return p;}
    public List<Long> seeds(){return Arrays.stream(text("seeds","42,73,101").split(",")).map(String::trim).map(Long::parseLong).toList();}
    public List<Long> budgets(){return Arrays.stream(text("budgets.ms","3000").split(",")).map(String::trim).map(Long::parseLong).toList();}
    public long effectiveBudget(long requested){return text("mode","TIME").equals("FIXED")?0:requested;}
    public Map<String,String> asMap(){Map<String,String> m=new TreeMap<>();values.forEach((k,v)->m.put(k.toString(),v.toString()));return m;}
    public void validate(){
        if(!Set.of("TIME","FIXED").contains(text("mode","TIME"))) throw new IllegalArgumentException("mode must be TIME or FIXED");
        if(seeds().isEmpty() || new HashSet<>(seeds()).size()!=seeds().size()) throw new IllegalArgumentException("Unique nonempty seeds required");
        if(budgets().isEmpty() || new HashSet<>(budgets()).size()!=budgets().size())throw new IllegalArgumentException("Unique nonempty budgets required");
        if(text("mode","TIME").equals("FIXED") && budgets().size()!=1)throw new IllegalArgumentException("FIXED mode takes exactly one nominal budget (not measured time)");
        if(budgets().stream().anyMatch(v->v<=0 || v>3_600_000)) throw new IllegalArgumentException("Each budget must be in 1..3600000 ms");
        for(String t:List.of("cars","motorcycles","bicycles")) {
            int n=integer("fleet."+t,t.equals("cars")?10:t.equals("motorcycles")?15:12);
            if(n<0 || n>99) throw new IllegalArgumentException("Fleet count must be 0..99: "+t);
        }
        double alpha=decimal("grasp.alpha",.3);
        if(!Double.isFinite(alpha)||alpha<0||alpha>1) throw new IllegalArgumentException("GRASP alpha outside [0,1]");
        if(integer("grasp.iterations",1000000)<=0) throw new IllegalArgumentException("Positive GRASP iterations required");
        new pe.edu.pucp.paqrap.planner.sa.AnnealingConfig(decimal("sa.temperature",1000),decimal("sa.minimumTemperature",1),
                decimal("sa.cooling",.95),integer("sa.iterationsPerTemperature",50),integer("sa.maximumIterations",1000000),integer("sa.maximumWithoutImprovement",1000000));
        if(!Set.of("KEEP","EXCLUDE").contains(text("orders.expiredPolicy","KEEP")))throw new IllegalArgumentException("orders.expiredPolicy must be KEEP or EXCLUDE");
        if(flag("maintenance.enabled",false)||flag("breakdowns.enabled",false))throw new IllegalArgumentException("Maintenance and automatic breakdowns are excluded from numerical experiments");
        for(String t:List.of("car","motorcycle","bicycle")){
            double speed=decimal("speed."+t,t.equals("car")?40:t.equals("motorcycle")?25:12);
            if(!Double.isFinite(speed)||speed<1||speed>1000)throw new IllegalArgumentException("Speed must be finite and within 1..1000 km/h");
        }
        for(String w:List.of("central","northwest","east")){
            int dx=w.equals("central")?27:w.equals("northwest")?12:57,dy=w.equals("central")?14:w.equals("northwest")?38:27;
            new pe.edu.pucp.paqrap.planner.domain.Location(integer(w+".x",dx),integer(w+".y",dy));
        }
        for(String w:List.of("northwest","east"))if(integer("stock."+w,1000)<0||integer("stock."+w,1000)>1000)throw new IllegalArgumentException("Stock must be in 0..1000");
        double maxLeg=decimal("routing.maxLegKm",0);
        if(!Double.isFinite(maxLeg)||maxLeg<0)throw new IllegalArgumentException("routing.maxLegKm must be finite and nonnegative");
        new pe.edu.pucp.paqrap.planner.domain.ShiftSchedule(pe.edu.pucp.paqrap.planner.domain.ShiftSchedule.DEFAULT_ZONE,integer("meal.startOffsetMinutes",180));
        if(integer("worker.heap.mb",512)<64||integer("worker.processors",2)<1||integer("worker.timeout.seconds",90)<1||integer("warmup.rounds",1)<0)
            throw new IllegalArgumentException("Invalid worker/warmup settings");
    }
}
