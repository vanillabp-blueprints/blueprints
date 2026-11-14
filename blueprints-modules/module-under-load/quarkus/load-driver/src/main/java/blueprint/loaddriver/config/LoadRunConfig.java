package blueprint.loaddriver.config;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * The configuration of the load driver, below {@code load-run} in {@code application.yaml}.
 */
@ConfigMapping(prefix = "load-run")
public interface LoadRunConfig {

  /**
   * @return The load profile on the class path.
   */
  Optional<String> profile();

  /**
   * @return How many loan requests may be handed in at the same time.
   */
  @WithDefault("0")
  int customerThreads();

  /**
   * @return The run sizes, by name.
   */
  Map<String, Size> sizes();

  /**
   * One run size.
   */
  interface Size {

    /**
     * @return How long each stage lasts, from the warm up to the peak.
     */
    Duration stageLength();

    /**
     * @return How many steps of more load come between the steady stage and the peak.
     */
    int steps();

    /**
     * @return How long the drain may take before the run gives up on the work left.
     */
    Duration drainAtMost();

  }

}
