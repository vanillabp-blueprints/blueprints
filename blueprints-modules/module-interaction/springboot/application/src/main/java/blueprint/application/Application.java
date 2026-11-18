package blueprint.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application. It contains no business code at all: it pulls in two workflow modules
 * and decides, by its Maven dependencies, which BPMS adapter is loaded.
 *
 * <p>
 * Note the package. It is not above the workflow modules, as in every blueprint. That is
 * the whole point: a workflow module is a JAR which may come from
 * another team and live in a package this application never heard of. Each module brings
 * an auto-configuration and wires itself, so this class stays empty no matter how many
 * modules are added.
 * </p>
 *
 * <p>
 * There is nothing here about the two modules. Not a scan, not an import, not a property.
 * Adding a third one is a line in the POM.
 * </p>
 */
@SpringBootApplication
public class Application {

  public static void main(
      String[] args) {

    SpringApplication.run(Application.class, args);

  }

}
