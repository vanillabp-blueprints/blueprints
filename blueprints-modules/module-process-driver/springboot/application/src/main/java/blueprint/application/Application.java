package blueprint.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application. It contains no business code: it pulls in the workflow module and the
 * adapters, and its Maven dependencies decide which adapters these are.
 *
 * <p>
 * Nothing here mocks a system around the application. In the profile {@code local} the adapters
 * talk to the simulator, in the profile {@code real} to the real systems. The code is the same.
 * </p>
 */
@SpringBootApplication
public class Application {

  public static void main(
      final String[] args) {

    SpringApplication.run(Application.class, args);

  }

}
