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
    public boolean flag(String key,boolean fallback){return Boolean.parseBoolean(text(key,Boolean.toString(fallback)));}
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
        if(budgets().stream().anyMatch(v->v<=0 || v>3_600_000)) throw new IllegalArgumentException("Each budget must be in 1..3600000 ms");
        for(String t:List.of("cars","motorcycles","bicycles")) {
            int n=integer("fleet."+t,t.equals("cars")?10:t.equals("motorcycles")?15:12);
            if(n<0 || n>99) throw new IllegalArgumentException("Fleet count must be 0..99: "+t);
        }
        double alpha=decimal("grasp.alpha",.3);
        if(!Double.isFinite(alpha)||alpha<0||alpha>1) throw new IllegalArgumentException("GRASP alpha outside [0,1]");
        if(integer("grasp.iterations",1000000)<=0) throw new IllegalArgumentException("Positive GRASP iterations required");
    }
}
