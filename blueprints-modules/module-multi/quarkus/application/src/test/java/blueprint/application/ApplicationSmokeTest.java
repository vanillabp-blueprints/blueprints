package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.vanillabp.spi.process.ProcessService;
import jakarta.enterprise.util.TypeLiteral;

/**
 * The smoke test of the application: does it start, is every workflow module found, and is
 * every BPMN task wired to code?
 *
 * <p>
 * Booting is the bigger half of the assertion. VanillaBP validates the wiring between BPMN
 * and code while the application is built and started, so an application which comes up
 * means every BPMN task has its {@code @WorkflowTask} method and the other way round.
 * </p>
 *
 * <p>
 * The counted assertion is what this blueprint adds to the shared one: <strong>both</strong>
 * modules have to be there. One {@code ProcessService} bean is what an application with a
 * forgotten dependency or a module without an index of its classes looks like, and it is a
 * state the application starts in perfectly happily.
 * </p>
 */
@QuarkusTest
public class ApplicationSmokeTest {

  @Test
  public void theApplicationStartsAndBothModulesAreWired() {

    final var processServices = Arc
        .container()
        .beanManager()
        .getBeans(new TypeLiteral<ProcessService<?>>() {
        }.getType());

    assertThat(processServices)
        .describedAs(
            "Expected one ProcessService bean per workflow module. A module is missing:"
                + " check its dependency, its META-INF/workflow-module file and the index"
                + " its build writes.")
        .hasSize(2);

  }

}
