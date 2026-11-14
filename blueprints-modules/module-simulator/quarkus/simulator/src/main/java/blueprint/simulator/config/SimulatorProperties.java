package blueprint.simulator.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The data the simulator starts with, read from {@code simulator.*}. It is there for a person
 * who tries the blueprint in a browser. A test does not rely on it: it creates its own data
 * through the test API.
 *
 * @param creditScores The credit score of each customer the credit rating knows from the start.
 */
@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
                                  @DefaultValue Map<String, Integer> creditScores) {
}
