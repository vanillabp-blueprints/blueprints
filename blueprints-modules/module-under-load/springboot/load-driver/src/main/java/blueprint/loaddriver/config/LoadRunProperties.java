package blueprint.loaddriver.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import blueprint.loaddriver.RunSize;

/**
 * The configuration of the load driver, below {@code load-run} in {@code application.yaml}.
 *
 * @param profile         The load profile on the class path.
 * @param customerThreads How many loan requests may be handed in at the same time.
 * @param sizes           The run sizes, by name.
 */
@ConfigurationProperties(prefix = "load-run")
public record LoadRunProperties(
                                String profile,
                                int customerThreads,
                                Map<String, RunSize> sizes) {
}
