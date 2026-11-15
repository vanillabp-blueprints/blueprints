package blueprint.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The simulator: a small application of its own, which puts the application under load.
 *
 * <p>
 * It holds the load driver. The driver plays the customers and the clerks, who call the API of
 * the application, and the credit bureau, which the application calls. It reads the meters the
 * application publishes, and it writes the report. None of this runs in the application. So the
 * driver does not share the CPU, the heap or the garbage collector with what it measures.
 * </p>
 */
@SpringBootApplication
public class SimulatorApplication {

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
