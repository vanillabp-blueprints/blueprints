package blueprint.loaddriver;

import java.time.Duration;

/**
 * How large a run is. The load profile says what the load looks like, the size says how long
 * the run plays it and how far it raises it.
 *
 * @param stageLength How long each stage lasts, from the warm up to the peak.
 * @param steps       How many steps of more load come between the steady stage and the peak.
 *                    Each step offers a quarter more than the steady stage.
 * @param drainAtMost How long the drain may take before the run gives up on the work left.
 */
public record RunSize(
                      Duration stageLength,
                      int steps,
                      Duration drainAtMost) {
}
