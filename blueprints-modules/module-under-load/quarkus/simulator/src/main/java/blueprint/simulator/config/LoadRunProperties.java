package blueprint.simulator.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import blueprint.simulator.loadrun.RunSize;

/**
 * The configuration of the load driver, below {@code load-run} in the {@code application.yaml}
 * of the simulator.
 *
 * @param profile         The load profile on the class path.
 * @param customerThreads How many loan requests may be handed in at the same time.
 * @param sizes           The run sizes, by name.
 * @param application     Where the application under load is, and how it names its pool.
 */
@ConfigurationProperties(prefix = "load-run")
public record LoadRunProperties(
                                String profile,
                                int customerThreads,
                                Map<String, RunSize> sizes,
                                Application application) {

  /**
   * The application under load, as the simulator reaches it from outside.
   *
   * @param baseUrl                 Where its API answers.
   * @param metricsUrl              Where it shows its meters in the Prometheus format.
   * @param jmxAddress              Where its JVM answers on JMX, as {@code host:port}.
   * @param connectionsWaitingMeter The gauge of its connection pool which counts the threads
   *                                waiting for a connection, in the Prometheus format.
   * @param poolSetting             The setting which sizes that pool, for the report.
   */
  public record Application(
                            String baseUrl,
                            String metricsUrl,
                            String jmxAddress,
                            String connectionsWaitingMeter,
                            String poolSetting) {
  }

}
