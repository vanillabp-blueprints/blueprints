package blueprint.workflowmodule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application. It has no business code. It takes the workflow module and the JAR of its
 * user interface as dependencies, and its Maven profile decides which BPMS adapter and which
 * cockpit adapter are loaded.
 *
 * <p>
 * Its package is the parent package of the workflow module, so component scanning, entity
 * scanning and Spring Data repositories of the module are picked up without any
 * configuration. The user interface needs no code either. Spring Boot serves the files below
 * {@code META-INF/resources/} of every JAR on the class path.
 * </p>
 */
@SpringBootApplication
public class Application {

  public static void main(
      String[] args) {

    SpringApplication.run(Application.class, args);

  }

}
