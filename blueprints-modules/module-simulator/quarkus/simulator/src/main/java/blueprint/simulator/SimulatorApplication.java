package blueprint.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import blueprint.simulator.config.SimulatorProperties;
import io.vanillabp.cockpit.devshell.simulator.DevShellSimulator;

/**
 * The simulator: one small application which plays every system the workflow module talks to.
 *
 * <p>
 * It extends the dev shell simulator of the Business Cockpit, so it plays the cockpit as well.
 * The scan names both packages: this one for the simulated credit rating and e-signature
 * service, and the one of {@link DevShellSimulator} for the cockpit.
 * </p>
 *
 * <p>
 * Every simulated system has two interfaces here. The first is the API of the real system, the
 * one the adapters of the application talk to. The second is the test API below
 * {@code /simulator/test-api/{system}/{action}}. Tests and people use it to prepare data and to
 * make a system act, for example to sign a contract. The test API knows nothing about the
 * workflow. It creates data in a system and makes the system do what the real one would do.
 * </p>
 */
@SpringBootApplication(scanBasePackageClasses = {
    SimulatorApplication.class, DevShellSimulator.class
})
@EnableConfigurationProperties(SimulatorProperties.class)
public class SimulatorApplication extends DevShellSimulator {

  /**
   * Starts the simulator.
   *
   * @param args The command line arguments, passed on to Spring Boot.
   */
  public static void main(
      final String[] args) {

    SpringApplication.run(SimulatorApplication.class, args);

  }

}
