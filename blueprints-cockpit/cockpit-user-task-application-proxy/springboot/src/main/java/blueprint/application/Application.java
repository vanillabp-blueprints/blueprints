package blueprint.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application. It contains no business code at all. It pulls in the workflow module and
 * decides, by its Maven dependencies, which BPMS adapter is loaded.
 *
 * <p>
 * Note the package. It is not above the package of the workflow module, and it does not need
 * to be. The module brings an auto-configuration, which wires its beans, its entities and its
 * repositories. It also brings the route which forwards its user interface to the nginx. This
 * class says nothing about the module, and adding another one is a line in the POM.
 * </p>
 */
@SpringBootApplication
public class Application {

  public static void main(
      String[] args) {

    SpringApplication.run(Application.class, args);

  }

}
