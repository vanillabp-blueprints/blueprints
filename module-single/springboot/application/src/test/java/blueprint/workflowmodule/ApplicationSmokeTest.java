package blueprint.workflowmodule;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The smoke test of the application: does the context start, are all workflow modules
 * found and is every BPMN task wired to code?
 *
 * <p>
 * It looks trivial and is not: VanillaBP validates the wiring between BPMN and code while
 * booting, so a failing context start is a real finding. Additional JARs in one runtime
 * can also interfere in ways an isolated module test never sees.
 * </p>
 */
@SpringBootTest
public class ApplicationSmokeTest {

  @Test
  public void theApplicationStartsAndEveryProcessIsWired() {

    // Booting is the assertion.

  }

}
