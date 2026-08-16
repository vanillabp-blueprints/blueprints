package blueprint.workflowmodule;

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
 * and code while the application starts, so an application which comes up means every BPMN
 * task has its {@code @WorkflowTask} method and the other way round. A failing start is a
 * real finding, and its message says what is missing.
 * </p>
 *
 * <p>
 * The explicit assertion covers the case booting cannot: an application that starts
 * perfectly well because it contains no workflow module at all - a JAR left out of the
 * dependencies, a marker file missing after a rename, or a module whose classes never made
 * it into an index.
 * </p>
 *
 * <p>
 * A smoke test exists in addition to the tests of the workflow modules because several
 * modules in one runtime can interfere in ways an isolated module test never sees.
 * </p>
 *
 * <p>
 * Part of the blueprint test harness: identical in every blueprint, kept in sync from
 * {@code templates/test-harness/quarkus/} of the monorepo. Do not edit it here.
 * </p>
 */
@QuarkusTest
public class ApplicationSmokeTest {

  @Test
  public void theApplicationStartsAndEveryWorkflowIsWired() {

    final var processServices = Arc
        .container()
        .beanManager()
        .getBeans(new TypeLiteral<ProcessService<?>>() {
        }.getType());

    assertThat(processServices)
        .describedAs(
            "No ProcessService bean exists, so no workflow module was detected."
                + " Check the module's dependency, its META-INF/workflow-module file"
                + " and the index its build writes.")
        .isNotEmpty();

  }

}
