package blueprint.workflowmodule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application, which is also the workflow module: one artifact, one deployment, one use
 * case. The Maven dependencies decide which BPMS adapter is loaded, and nothing else about
 * this class differs from the one in a project whose workflow module is a JAR of its own.
 *
 * <p>
 * That the application is the workflow module is said in one file,
 * {@code src/main/resources/META-INF/workflow-module}, and it contains the module's ID.
 * There is no way around naming it: a workflow module ID is what the BPMS sees, what
 * configuration files are named after, and what keeps the identifiers of one application
 * apart from those of the next one on the same engine.
 * </p>
 *
 * <p>
 * The package layout is the one of every other blueprint. Nothing here depends on the
 * application and the use case sharing an artifact, which is what makes splitting the use
 * case out later a move rather than a rewrite.
 * </p>
 */
@SpringBootApplication
public class Application {

  public static void main(
      String[] args) {

    SpringApplication.run(Application.class, args);

  }

}
