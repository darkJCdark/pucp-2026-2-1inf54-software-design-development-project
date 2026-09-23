package pe.edu.pucp.paqrap.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Portable report: open locally in a browser, without a server or JavaScript dependencies. */
public final class HtmlReport {
    private HtmlReport(){}
    public static void write(Path out,List<Map<String,String>> rows)throws IOException{
        StringBuilder h=new StringBuilder("<!doctype html><html lang='es'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>PaqRap · GRASP vs SA</title><style>body{margin:0;background:#f5f6f8;color:#17212d;font:15px system-ui,sans-serif}main{max-width:1350px;margin:auto;padding:36px}h1{font-size:32px;margin:0}h2{margin-top:32px}.tag{color:#bc481e;font-weight:700;font-size:13px;letter-spacing:1px}.note{background:#fff3dd;border-left:4px solid #d08b00;padding:16px;line-height:1.6}.cards{display:flex;flex-wrap:wrap;gap:16px;margin:24px 0}.card{background:white;padding:20px;border:1px solid #dce1e7;border-radius:10px;min-width:220px}.card strong{display:block;font-size:27px}table{width:100%;border-collapse:collapse;background:white;font-size:13px}th,td{text-align:left;padding:11px;border-bottom:1px solid #e0e4e8}th{background:#17212d;color:white;white-space:nowrap}.scroll{overflow-x:auto}a{color:#1b5b96}p{line-height:1.6}.ok{color:#16734e;font-weight:700}.bad{color:#9a4124;font-weight:700}footer{margin:32px 0;color:#5e6a78}</style><main><p class='tag'>PAQRAP / EXPERIMENTACIÓN NUMÉRICA</p><h1>GRASP frente a Simulated Annealing</h1><p>Resultados de las implementaciones unificadas. Cada pareja recibe la misma instancia y dispone del mismo presupuesto máximo de tiempo.</p>");
        h.append("<div class='note'><b>Interpretación:</b> “OK” significa que el evaluador común valida un plan que cubre TODA la demanda. Un plan parcial barato no es un éxito. “No se encontró” no demuestra que el problema sea imposible. “No atendibles” son pedidos que ninguna ruta directa desde el central alcanza a tiempo con ningún vehículo disponible: ningún algoritmo puede cubrirlos; “cubren todo lo atendible” exige rutas válidas que entreguen todos los demás. Se comparan decisiones de planificación estática, no una simulación online de cinco días.</div><div class='cards'>");
        for(String alg:List.of("GRASP","SA")){
            var rr=rows.stream().filter(r->alg.equals(r.get("algorithm"))).toList();long n=rr.size(),ok=rr.stream().filter(HtmlReport::feasible).count();
            long okServable=rr.stream().filter(r->"true".equals(r.get("full_servable_feasible"))).count();
            h.append("<div class='card'>").append(alg).append("<strong>").append(ok).append(" / ").append(n).append("</strong>soluciones completas factibles<br>")
             .append(okServable).append(" / ").append(n).append(" cubren todo lo atendible</div>");
        }
        h.append("<div class='card'>Corridas registradas<strong>").append(rows.size()).append("</strong>incluye fallos y límites de tiempo</div></div><p><a href='runs.csv'>Resultados completos CSV</a> · <a href='metadata.json'>Entorno y protocolo</a> · <a href='experiment.properties'>Configuración</a> · <a href='instances.csv'>Instancias</a></p><h2>Resultados por corrida</h2><div class='scroll'><table><tr><th>Instancia</th><th>Semilla</th><th>Algoritmo</th><th>Estado</th><th>Atendidos / total</th><th>No atendibles</th><th>Costo factible S/</th><th>Tiempo ms</th><th>Terminación</th><th>Detalle</th></tr>");
        for(var r:rows){
            h.append("<tr>");for(String k:List.of("instance_id","search_seed","algorithm","status"))h.append("<td>").append(esc(r.get(k))).append("</td>");
            h.append("<td>").append(esc(r.get("orders_fully_served"))).append(" / ").append(esc(r.get("orders_total"))).append("</td>");
            h.append("<td>").append(esc(r.get("orders_provably_unservable"))).append("</td>");
            for(String k:List.of("cost_complete_feasible","elapsed_ms","termination"))h.append("<td>").append(esc(r.get(k))).append("</td>");
            String id=esc(r.get("run_id"));h.append("<td><a href='jobs/").append(id).append(".plan.json'>Plan</a> · <a href='jobs/").append(id).append(".log'>Log</a></td></tr>");
        }
        h.append("</table></div><h2>Comparación pareada de costo</h2><p>Solo para parejas en las que AMBOS cubrieron toda la demanda. Diferencia = costo GRASP − costo SA. No es un gap contra el óptimo.</p><table><tr><th>Instancia</th><th>Semilla</th><th>Presupuesto ms</th><th>Costo GRASP</th><th>Costo SA</th><th>Diferencia S/</th></tr>");
        Map<String,Map<String,Map<String,String>>> pairs=new LinkedHashMap<>();
        for(var r:rows){String key=r.get("instance_id")+"|"+r.get("search_seed")+"|"+r.get("budget_ms");pairs.computeIfAbsent(key,k->new HashMap<>()).put(r.get("algorithm"),r);}
        StringBuilder paired=new StringBuilder("instance_id,search_seed,budget_ms,grasp_ok,sa_ok,grasp_cost,sa_cost,difference_grasp_minus_sa\n");
        for(var p:pairs.values()){
            var g=p.get("GRASP");var s=p.get("SA");if(g==null||s==null)continue;
            boolean both=feasible(g)&&feasible(s);String delta=both?Csv.number(Double.parseDouble(g.get("cost_complete_feasible"))-Double.parseDouble(s.get("cost_complete_feasible"))):"";
            paired.append(String.join(",",g.get("instance_id"),g.get("search_seed"),g.get("budget_ms"),Boolean.toString(feasible(g)),Boolean.toString(feasible(s)),g.getOrDefault("cost_complete_feasible",""),s.getOrDefault("cost_complete_feasible",""),delta)).append('\n');
            if(both){h.append("<tr>");for(String v:List.of(g.get("instance_id"),g.get("search_seed"),g.get("budget_ms"),g.get("cost_complete_feasible"),s.get("cost_complete_feasible"),delta))h.append("<td>").append(esc(v)).append("</td>");h.append("</tr>");}
        }
        h.append("</table><p><a href='paired.csv'>CSV de todas las parejas (incluidos fallos)</a></p><footer>No se declara un ganador con una prueba de humo. Analizar tasas de éxito, dispersión por instancia y costo condicionado a factibilidad. Ver docs/PROTOCOLO.md y docs/LIMITACIONES.md.</footer></main></html>");
        Files.writeString(out.resolve("paired.csv"),paired.toString(),StandardCharsets.UTF_8);
        Files.writeString(out.resolve("report.html"),h.toString(),StandardCharsets.UTF_8);
    }
    private static boolean feasible(Map<String,String> r){return "true".equals(r.get("full_feasible"));}
    private static String esc(String s){return s==null||s.isBlank()?"—":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
