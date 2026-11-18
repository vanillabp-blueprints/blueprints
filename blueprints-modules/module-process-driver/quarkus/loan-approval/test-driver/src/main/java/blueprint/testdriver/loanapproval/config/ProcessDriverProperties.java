package blueprint.testdriver.loanapproval.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of the process driver, below {@code process-driver}.
 */
@ConfigMapping(prefix = "process-driver")
public interface ProcessDriverProperties {

  /**
   * Switches the driver and its REST API on. It is off by default, so an application which has
   * this module on its classpath by mistake still shows nothing of it. It is a build property:
   * Quarkus reads it while it builds the application, and every bean of the driver says
   * {@code @IfBuildProperty} for it.
   *
   * @return Whether the driver is on.
   */
  @WithDefault("false")
  boolean enabled();

  /**
   * Where the simulator answers. The driver uses its test API to prepare data and to make a
   * system act. The default is where
   * {@code java -jar simulator/application/target/simulator-application-1.0.0-SNAPSHOT-runnable.jar}
   * starts it.
   *
   * @return The address of the simulator.
   */
  @WithDefault("http://localhost:8079")
  String simulatorBaseUrl();

  /**
   * Where the application answers. The driver uses the API of the user interface of the
   * workflow module there, as a person in the browser does. The default is where the application
   * of this blueprint starts.
   *
   * @return The address of the application.
   */
  @WithDefault("http://localhost:8080")
  String applicationBaseUrl();

  /**
   * How long a step waits for the application, one minute like the tests. One retry of the
   * outbox fits in.
   *
   * @return The time a step waits.
   */
  @WithDefault("PT1M")
  Duration timeout();

}
