package pe.edu.pucp.paqrap.experiment;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static pe.edu.pucp.paqrap.experiment.Json.obj;

/** Sequential, paired experiment controller. It never runs GRASP and SA concurrently. */
public final class ExperimentMain {
    public static void main(String[] args)throws Exception{
        if(args.length==1 && args[0].equals("--self-test")){VerificationMain.main(new String[0]);return;}
        Map<String,String> a=new HashMap<>();
        for(int i=0;i<args.length;i+=2){if(args[i].equals("--help")){usage();return;}if(i+1>=args.length)throw new IllegalArgumentException("Missing argument value");a.put(args[i],args[i+1]);}
        Path root=Path.of("").toAbsolutePath().normalize();
        Path configFile=root.resolve(a.getOrDefault("--config","config/smoke.properties"));
        ExperimentConfig config=ExperimentConfig.read(configFile,root);config.validate();
        List<ScenarioSpec> specs=Files.readAllLines(config.path("instances.file","config/smoke.csv"),StandardCharsets.UTF_8).stream()
                .map(String::strip).filter(s->!s.isEmpty()&&!s.startsWith("#")&&!s.startsWith("id,")).map(ScenarioSpec::parse).toList();
        if(specs.isEmpty())throw new IllegalArgumentException("Empty instance set");
        if(specs.stream().map(ScenarioSpec::id).distinct().count()!=specs.size())throw new IllegalArgumentException("Duplicate instance ids");
        String stamp=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path out=root.resolve(a.getOrDefault("--output","results/"+config.text("name","experiment")+"-"+stamp));
        boolean resume=Boolean.parseBoolean(a.getOrDefault("--resume","false"));
        if(Files.exists(out.resolve("runs.csv"))&&!resume)throw new IllegalArgumentException("Output already contains results. Choose another --output or use --resume true with the same build/configuration.");
        Files.createDirectories(out.resolve("jobs"));Files.createDirectories(out.resolve("instances"));
        Files.copy(configFile,out.resolve("experiment.properties"),StandardCopyOption.REPLACE_EXISTING);
        Files.copy(config.path("instances.file","config/smoke.csv"),out.resolve("instances.csv"),StandardCopyOption.REPLACE_EXISTING);
        String classpath=absoluteClasspath();
        String identity=Json.sha256(Json.encode(obj("config",config.asMap(),"instances",Files.readString(config.path("instances.file","config/smoke.csv")),"classpath",classpathHashes(classpath))));
        Path identityFile=out.resolve("run.identity");
        if(resume && (!Files.exists(identityFile)||!Files.readString(identityFile).strip().equals(identity)))
            throw new IllegalArgumentException("Resume rejected: build, configuration or instance definitions changed. Use a NEW output directory.");
        if(!resume)Files.writeString(identityFile,identity+"\n",StandardCharsets.UTF_8);
        Map<String,Object> metadata=obj("generated_at_utc",Instant.now(),"java_version",System.getProperty("java.version"),"java_vendor",System.getProperty("java.vendor"),
                "os",System.getProperty("os.name"),"os_version",System.getProperty("os.version"),"arch",System.getProperty("os.arch"),
                "available_processors_parent",Runtime.getRuntime().availableProcessors(),"worker_heap_mb",config.integer("worker.heap.mb",512),
                "worker_active_processor_count",config.integer("worker.processors",2),"warmup_rounds_both_algorithms",config.integer("warmup.rounds",1),
                "settings",config.asMap(),"config_sha256",Json.sha256(Json.encode(config.asMap())),"classpath_sha256",classpathHashes(classpath),
                "protocol","Each trial in a fresh child JVM; paired inputs and search seeds; alternating order. IO/startup/warmup/final audit excluded from search time; SA initialization included.");
        if(!resume)Files.writeString(out.resolve("metadata.json"),Json.encode(metadata),StandardCharsets.UTF_8);
        Path csv=out.resolve("runs.csv");Files.writeString(csv,String.join(",",Csv.COLUMNS)+"\n",StandardCharsets.UTF_8);
        List<Map<String,String>> results=new ArrayList<>();int count=0,total=specs.size()*config.seeds().size()*config.budgets().size()*2;
        for(int si=0;si<specs.size();si++){
            ScenarioSpec spec=specs.get(si);ProblemInstance instance=InstanceFactory.create(spec,config);
            Files.writeString(out.resolve("instances/"+spec.id()+".json"),Json.encode(instance.manifest()),StandardCharsets.UTF_8);
            for(long budget:config.budgets())for(int rep=0;rep<config.seeds().size();rep++){
                long seed=config.seeds().get(rep);
                List<String> order=(si+rep)%2==0?List.of("GRASP","SA"):List.of("SA","GRASP");
                for(String algorithm:order){
                    String id=spec.id()+"-r"+(rep+1)+"-b"+budget+"-"+algorithm;
                    Path prefix=out.resolve("jobs/"+id),jobFile=Path.of(prefix+".job.properties");
                    Path completedResult=Path.of(prefix+".result.properties");
                    if(resume && Files.exists(completedResult)) {
                        Map<String,String> saved=readResult(completedResult);
                        if(!instance.sha256().equals(saved.get("input_sha256")))
                            throw new IllegalArgumentException("Resume input changed for "+id);
                        results.add(saved);Files.writeString(csv,Csv.row(saved)+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
                        HtmlReport.write(out,results);
                        System.out.printf("[%d/%d] %s | retained %s%n",++count,total,id,saved.get("status"));
                        continue;
                    }
                    Properties job=new Properties();
                    job.setProperty("root",root.toString());job.setProperty("config",configFile.toString());job.setProperty("scenario",spec.csv());
                    job.setProperty("algorithm",algorithm);job.setProperty("run_id",id);job.setProperty("seed",Long.toString(seed));
                    job.setProperty("budget",Long.toString(budget));job.setProperty("repetition",Integer.toString(rep+1));job.setProperty("prefix",prefix.toString());job.setProperty("expected_input_sha256",instance.sha256());
                    try(var w=Files.newBufferedWriter(jobFile,StandardCharsets.UTF_8)){job.store(w,"Reproducible worker job");}
                    String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
                    List<String> command=List.of(java,"-Xms64m","-Xmx"+config.integer("worker.heap.mb",512)+"m","-XX:ActiveProcessorCount="+config.integer("worker.processors",2),"-Dfile.encoding=UTF-8","-cp",classpath,TrialMain.class.getName(),jobFile.toString());
                    System.out.printf(Locale.ROOT,"[%d/%d] %s | %d orders | budget %d ms ...%n",++count,total,id,instance.orders().size(),config.effectiveBudget(budget));
                    Process process=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(Path.of(prefix+".log").toFile()).start();
                    long guard=Math.max(config.integer("worker.timeout.seconds",90),budget/1000+30);
                    boolean finished=process.waitFor(guard,TimeUnit.SECONDS);
                    if(!finished){process.destroyForcibly();process.waitFor();}
                    Map<String,String> row=new LinkedHashMap<>();Path resultFile=Path.of(prefix+".result.properties");
                    if(finished&&process.exitValue()==0&&Files.exists(resultFile)){
                        Properties r=new Properties();try(var reader=Files.newBufferedReader(resultFile,StandardCharsets.UTF_8)){r.load(reader);}r.forEach((k,v)->row.put(k.toString(),v.toString()));
                    }else{
                        TrialMain.put(row,"run_id",id,"instance_id",spec.id(),"family",spec.family(),"source",spec.source(),"search_seed",seed,"instance_seed",spec.instanceSeed(),"repetition",rep+1,"algorithm",algorithm,"mode",config.text("mode","TIME"),"budget_ms",config.effectiveBudget(budget),"status",finished?"WORKER_ERROR":"HARD_TIMEOUT","full_feasible",false,"orders_total",instance.orders().size(),"input_sha256",instance.sha256(),"config_sha256",Json.sha256(Json.encode(config.asMap())),"detail","Worker did not produce a result. Inspect jobs/"+id+".log");
                    }
                    if(!Files.exists(resultFile)){
                        Properties failureResult=new Properties();failureResult.putAll(row);
                        try(var writer=Files.newBufferedWriter(resultFile,StandardCharsets.UTF_8)){failureResult.store(writer,"Failure retained in the experiment, not retried by resume");}
                    }
                    results.add(row);Files.writeString(csv,Csv.row(row)+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
                    HtmlReport.write(out,results);
                    System.out.println("  "+row.get("status")+" | coverage="+row.getOrDefault("coverage_orders_pct","?")+"% | cost(full)="+row.getOrDefault("cost_complete_feasible","")+" | ms="+row.getOrDefault("elapsed_ms","?"));
                }
            }
        }
        System.out.println("Done. Open: "+out.resolve("report.html"));
        System.out.println("Raw, paired results: "+csv);
    }
    private static String absoluteClasspath(){return String.join(File.pathSeparator,Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))).map(s->Path.of(s).toAbsolutePath().toString()).toList());}
    private static Map<String,Object> classpathHashes(String cp){Map<String,Object> m=new LinkedHashMap<>();for(String s:cp.split(java.util.regex.Pattern.quote(File.pathSeparator))){try{Path p=Path.of(s);m.put(p.getFileName().toString(),Files.isRegularFile(p)?Json.sha256(Files.readAllBytes(p)):"compiled directory; see source manifest");}catch(IOException e){m.put(s,"unreadable");}}return m;}
    private static Map<String,String> readResult(Path p)throws IOException{
        Properties v=new Properties();try(var reader=Files.newBufferedReader(p,StandardCharsets.UTF_8)){v.load(reader);}
        Map<String,String> result=new LinkedHashMap<>();v.forEach((k,x)->result.put(k.toString(),x.toString()));return result;
    }
    private static void usage(){System.out.println("From project root: java -jar dist/paqrap-experimentos.jar --config config/smoke.properties [--output results/my-run] [--resume true]");}
}
