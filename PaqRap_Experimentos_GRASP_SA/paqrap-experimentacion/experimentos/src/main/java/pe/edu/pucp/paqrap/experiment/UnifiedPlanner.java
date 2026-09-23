package pe.edu.pucp.paqrap.experiment;

/** Common entry point. The algorithms retain their own construction and search strategies. */
public interface UnifiedPlanner {
    String name();
    AlgorithmOutput solve(ProblemInstance instance, ExperimentConfig config, long seed);
}
