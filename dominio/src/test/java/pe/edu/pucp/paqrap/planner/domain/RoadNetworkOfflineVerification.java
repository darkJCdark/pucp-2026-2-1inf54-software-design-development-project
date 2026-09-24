package pe.edu.pucp.paqrap.planner.domain;
import java.time.*;
import java.util.*;
/** Dependency-free differential test; reference includes the exact activation-boundary safety fix. */
public final class RoadNetworkOfflineVerification {
    public static void main(String[] args) {
        Instant base=Instant.parse("2026-09-09T12:00:00Z");Random rng=new Random(20260924L);
        RoadNetwork optimized=new RoadNetwork();ReferenceRoadNetwork reference=new ReferenceRoadNetwork();
        int checked=0;
        for(int group=0;group<25;group++){
            List<RoadBlock> blocks=new ArrayList<>();
            for(int b=0;b<group%8;b++){
                int x=1+rng.nextInt(60),y=1+rng.nextInt(40);
                Instant start=base.plusSeconds(rng.nextInt(4*3600));
                blocks.add(new RoadBlock(start,start.plusSeconds(100+rng.nextInt(7200)),List.of(new Location(x,y),new Location(x+1+rng.nextInt(8),y))));
            }
            List<RoadBlock> passed=group%2==0?List.copyOf(blocks):new ArrayList<>(blocks);
            for(int q=0;q<20;q++){
                Location origin=new Location(rng.nextInt(71),rng.nextInt(51));
                Location dest=q%5==0?origin:new Location(rng.nextInt(71),rng.nextInt(51));
                Instant departure=base.plusSeconds(rng.nextInt(8*3600)).plusNanos(rng.nextInt(1000000000));
                Duration duration=List.of(Duration.ofSeconds(90),Duration.ofSeconds(144),Duration.ofSeconds(300)).get(rng.nextInt(3));
                var expected=reference.shortestPath(origin,dest,departure,duration,blocks);
                for(int repeat=0;repeat<2;repeat++){
                    var actual=optimized.shortestPath(origin,dest,departure,duration,passed);
                    if(expected.isPresent()!=actual.isPresent() || expected.isPresent() && (!expected.get().legs().equals(actual.get().legs()) || !expected.get().arrivesAt().equals(actual.get().arrivesAt())))
                        throw new AssertionError("Different cached path at group="+group+" query="+q);
                }
                Location adjacent=origin.x()<70?new Location(origin.x()+1,origin.y()):new Location(origin.x()-1,origin.y());
                if(!reference.traverse(origin,adjacent,departure,duration,blocks).equals(optimized.traverse(origin,adjacent,departure,duration,passed)))
                    throw new AssertionError("Different traversal at group="+group+" query="+q);
                checked++;
            }
        }
        System.out.println("PASS: "+checked+" differential scenarios; 1000 path comparisons including cache, and 500 traversal comparisons.");
    }
}
