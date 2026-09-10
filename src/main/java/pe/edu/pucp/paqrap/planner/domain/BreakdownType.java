package pe.edu.pucp.paqrap.planner.domain;

import java.time.Duration;

/** Course-defined vehicle breakdown classes. */
public enum BreakdownType {
    MINOR(Duration.ofHours(2), Duration.ZERO),
    INTERMEDIATE(Duration.ofHours(4), Duration.ofHours(4)),
    MAJOR(Duration.ofDays(2), Duration.ofHours(4));

    private final Duration minimumUnavailable;
    private final Duration maximumRoadsideDuration;

    BreakdownType(Duration minimumUnavailable, Duration maximumRoadsideDuration) {
        this.minimumUnavailable = minimumUnavailable;
        this.maximumRoadsideDuration = maximumRoadsideDuration;
    }

    public Duration minimumUnavailable() { return minimumUnavailable; }
    public Duration maximumRoadsideDuration() { return maximumRoadsideDuration; }
}
