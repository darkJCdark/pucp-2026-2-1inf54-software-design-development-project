package pe.edu.pucp.paqrap.planner.search;

import java.util.ArrayList;
import java.util.List;

/** Optional per-thread instrumentation. No installed control => original unlimited API. */
public final class SearchControl implements AutoCloseable {
    private static final ThreadLocal<SearchControl> ACTIVE = new ThreadLocal<>();
    private final long started = System.nanoTime();
    private final long limitNanos;
    private long finished;
    private boolean timedOut;
    private long evaluations, schedules, paths, iterations, attempts, invalid, accepted;
    private long checks, sampledHeap;
    private int fewestMissing = Integer.MAX_VALUE;
    private double bestCost = Double.POSITIVE_INFINITY;
    private double firstCompleteMs = Double.NaN;
    private final List<Sample> trace = new ArrayList<>();

    private SearchControl(long millis) {
        if (millis < 0 || millis > 86_400_000L) throw new IllegalArgumentException("Invalid budget in milliseconds");
        limitNanos = millis * 1_000_000L;
        sampleMemory();
    }
    public static SearchControl install(long millis) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested search controls are not allowed");
        SearchControl c = new SearchControl(millis);
        ACTIVE.set(c);
        return c;
    }
    public static void checkpoint() {
        SearchControl c = ACTIVE.get();
        if (c == null) return;
        long elapsed = System.nanoTime() - c.started;
        if (Thread.currentThread().isInterrupted() || (c.limitNanos > 0 && elapsed >= c.limitNanos)) {
            c.timedOut = true;
            throw new SearchStopped();
        }
        if ((++c.checks & 1023) == 0) c.sampleMemory();
    }
    public static void planEvaluation() { checkpoint(); SearchControl c=ACTIVE.get(); if(c!=null)c.evaluations++; }
    public static void routeSchedule() { checkpoint(); SearchControl c=ACTIVE.get(); if(c!=null)c.schedules++; }
    public static void pathQuery() { checkpoint(); SearchControl c=ACTIVE.get(); if(c!=null)c.paths++; }
    public static void iteration() { checkpoint(); SearchControl c=ACTIVE.get(); if(c!=null)c.iterations++; }
    public static void neighborAttempt() { SearchControl c=ACTIVE.get(); if(c!=null)c.attempts++; }
    public static void invalidNeighbor() { SearchControl c=ACTIVE.get(); if(c!=null)c.invalid++; }
    public static void acceptedNeighbor() { SearchControl c=ACTIVE.get(); if(c!=null)c.accepted++; }
    public static void observe(boolean valid, int missing, double cost) {
        SearchControl c=ACTIVE.get();
        if(c==null || !valid || !Double.isFinite(cost)) return;
        double ms=(System.nanoTime()-c.started)/1e6;
        if(missing==0 && Double.isNaN(c.firstCompleteMs)) c.firstCompleteMs=ms;
        if(missing < c.fewestMissing || (missing==c.fewestMissing && cost < c.bestCost)) {
            c.fewestMissing=missing; c.bestCost=cost;
            c.trace.add(new Sample(ms, missing, cost));
        }
    }
    private void sampleMemory() {
        Runtime r=Runtime.getRuntime();
        sampledHeap=Math.max(sampledHeap,r.totalMemory()-r.freeMemory());
    }
    @Override public void close() {
        finished=System.nanoTime(); sampleMemory(); ACTIVE.remove();
    }
    public boolean timedOut(){return timedOut;}
    public double elapsedMs(){return ((finished==0?System.nanoTime():finished)-started)/1e6;}
    public long evaluations(){return evaluations;}
    public long schedules(){return schedules;}
    public long paths(){return paths;}
    public long iterations(){return iterations;}
    public long attempts(){return attempts;}
    public long invalid(){return invalid;}
    public long accepted(){return accepted;}
    public long sampledHeapBytes(){return sampledHeap;}
    public double firstCompleteMs(){return firstCompleteMs;}
    public List<Sample> trace(){return List.copyOf(trace);}
    public record Sample(double elapsedMs,int missing,double cost) {}
}
