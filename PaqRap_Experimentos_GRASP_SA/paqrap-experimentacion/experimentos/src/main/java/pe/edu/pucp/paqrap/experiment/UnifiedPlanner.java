package pe.edu.pucp.paqrap.experiment;

/** Common entry point. The algorithms retain their own construction and search strategies. */
public interface UnifiedPlanner {
    String name();
    /** Version de la implementacion medida; se exporta en cada corrida para no mezclar versiones. */
    default String version() { return name(); }
    AlgorithmOutput solve(ProblemInstance instance, ExperimentConfig config, long seed);
}
