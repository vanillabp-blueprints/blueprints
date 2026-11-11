package blueprint.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application. It has no business code. It takes the workflow module and the JAR of its
 * user interface as dependencies, and its Maven profile decides which BPMS adapter and which
 * cockpit adapter are loaded.
 *
 * <p>
 * Note the package. It is not above the package of the workflow module, and it does not need
 * to be. The module brings an auto-configuration, which wires its beans, its entities and its
 * repositories. The user interface needs no code either. Spring Boot serves the files below
 * {@code META-INF/resources/} of every JAR on the class path. So this class says nothing about
 * the module, and adding another one is a line in the POM.
 * </p>
 */
@SpringBootApplication
public class Application {

  public static void main(
      String[] args) {

    SpringApplication.run(Application.class, args);

  }

}
