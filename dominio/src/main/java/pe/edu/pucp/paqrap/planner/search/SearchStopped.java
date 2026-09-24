package pe.edu.pucp.paqrap.planner.search;

/** Control signal, NOT a business infeasibility or IllegalStateException. */
public final class SearchStopped extends RuntimeException {
    public SearchStopped() { super("Computational budget exhausted", null, false, false); }
}
