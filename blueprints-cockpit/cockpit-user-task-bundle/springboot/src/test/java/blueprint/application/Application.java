package blueprint.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application of the test: an application with several workflow modules, as it takes the
 * bundle. It has none of its own here, because the bundle is what is tested.
 *
 * <p>
 * Note the package. It is not above the package of the workflow module, so the component scan
 * of this class does not reach the module. The bundle brings an auto-configuration, and that
 * wires the module. This class says nothing about it. Adding another bundle is a line in the
 * POM.
 * </p>
 */
@SpringBootApplication
public class Application {

  public static void main(
      String[] args) {

    SpringApplication.run(Application.class, args);

  }

}
