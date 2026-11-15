package blueprint.testdriver.loanapproval.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration of the process driver, below {@code process-driver}.
 */
@ConfigurationProperties(prefix = "process-driver")
@Data
public class ProcessDriverProperties {

  /**
   * Switches the driver and its REST API on. It is off by default, so an application which has
   * this module on its classpath by mistake still shows nothing of it.
   */
  private boolean enabled = false;

  /**
   * Where the simulator answers. The driver uses its test API to prepare data and to make a
   * system act. The default is where {@code mvn -pl simulator spring-boot:run} starts it.
   */
  private String simulatorBaseUrl = "http://localhost:8079";

  /**
   * How long a step waits for the application, one minute like the tests. One retry of the
   * outbox fits in.
   */
  private Duration timeout = Duration.ofMinutes(1);

}
