package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import io.vanillabp.spi.process.ProcessService;

/**
 * The smoke test of the application: does the context start, is every workflow module
 * found, and is every BPMN task wired to code?
 *
 * <p>
 * Booting is the bigger half of the assertion. VanillaBP validates the wiring between BPMN
 * and code while the application starts, so a context which comes up means every BPMN task
 * has its {@code @WorkflowTask} method and the other way round.
 * </p>
 *
 * <p>
 * The counted assertion is what this blueprint adds to the shared one: <strong>both</strong>
 * modules have to be there. One {@code ProcessService} bean is what an application with a
 * forgotten dependency or a module whose auto-configuration is not registered looks like,
 * and it is a state the application starts in perfectly happily.
 * </p>
 *
 * <p>
 * The test lives next to the application class rather than in
 * {@code blueprint.workflowmodule}, where the other blueprints keep it: the application's
 * package is no longer above the modules, and a {@code @SpringBootTest} finds its
 * configuration by walking up from its own package.
 * </p>
 */
@SpringBootTest
public class ApplicationSmokeTest {

  @Autowired
  private ApplicationContext context;

  @Test
  public void theApplicationStartsAndBothModulesAreWired() {

    assertThat(context.getBeanNamesForType(ProcessService.class))
        .describedAs(
            "Expected one ProcessService bean per workflow module. A module is missing:"
                + " check its dependency, its META-INF/workflow-module file and the"
                + " auto-configuration it registers in META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports.")
        .hasSize(2);

  }

}
