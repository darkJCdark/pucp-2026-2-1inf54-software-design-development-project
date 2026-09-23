package pe.edu.pucp.paqrap.experiment;

import java.time.LocalDate;

/** One independent problem instance; search seeds are NOT instance seeds. */
public record ScenarioSpec(String id,String family,String source,int orders,long instanceSeed,
                           LocalDate date,int fromHour,int toHour) {
    public ScenarioSpec {
        if(!id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Unsafe instance id");
        if(!source.equals("SYNTHETIC")&&!source.equals("REAL")) throw new IllegalArgumentException("source must be SYNTHETIC or REAL");
        if(orders<0||fromHour<0||toHour>23||toHour<=fromHour) throw new IllegalArgumentException("Invalid instance interval/count");
    }
    public String csv(){return String.join(",",id,family,source,Integer.toString(orders),Long.toString(instanceSeed),date.toString(),Integer.toString(fromHour),Integer.toString(toHour));}
    public static ScenarioSpec parse(String line){
        String[] a=line.strip().split(",",-1);
        if(a.length!=8) throw new IllegalArgumentException("Instance CSV must have 8 columns: "+line);
        return new ScenarioSpec(a[0],a[1],a[2],Integer.parseInt(a[3]),Long.parseLong(a[4]),LocalDate.parse(a[5]),Integer.parseInt(a[6]),Integer.parseInt(a[7]));
    }
}
