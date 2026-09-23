package pe.edu.pucp.paqrap.experiment;
import java.util.*;
public final class Csv {
    private Csv(){}
    public static final List<String> COLUMNS=List.of("run_id","instance_id","family","source","instance_seed","search_seed","repetition","algorithm","mode","budget_ms","status","termination","initialization","full_feasible","route_constraints_valid","orders_total","orders_fully_served","orders_missing","packages_total","packages_covered_on_time","coverage_orders_pct","orders_provably_unservable","servable_orders_fully_served","coverage_servable_pct","full_servable_feasible","cost_complete_feasible","cost_raw_do_not_rank_incomplete","distance_km","routes","elapsed_ms","time_first_complete_ms","initialization_ms","final_audit_ms","iterations","neighbor_attempts","invalid_neighbors","accepted_neighbors","plan_evaluations","route_schedules","path_queries","heap_sampled_peak_mib","input_sha256","plan_sha256","config_sha256","algorithm_version","detail");
    public static String row(Map<String,String> m){return String.join(",",COLUMNS.stream().map(k->escape(m.getOrDefault(k,""))).toList());}
    public static String escape(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    public static String number(double d){return Double.isFinite(d)?String.format(Locale.ROOT,"%.6f",d):"";}
}
