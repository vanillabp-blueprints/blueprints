package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * The beans of a workflow module are named after the module, and this test is what keeps it
 * that way.
 *
 * <p>
 * Both modules have a class called {@code Service} and one called {@code ApiController}.
 * With Spring's default names the second module to be registered ends the boot, and the
 * message names a bean nobody wrote. Each module therefore generates its bean names as
 * {@code <module-id>_<SimpleName>}, which is also what a stack trace or an actuator listing
 * shows from then on.
 * </p>
 *
 * <p>
 * The application starting at all already proves that nothing collides. What this test adds
 * is the naming itself: a module which quietly falls back to the default names works today
 * and collides with the next module somebody adds.
 * </p>
 */
@SpringBootTest
public class BeanNamesPerModuleIT {

  @Autowired
  private ApplicationContext context;

  @Test
  public void everyModuleNamesItsBeansAfterItself() {

    assertThat(context.getBeanDefinitionNames())
        .describedAs("the beans of both modules, named by the generator each module brings")
        .contains(
            "loan-approval_Service",
            "loan-approval_ApiController",
            "loan-approval_AggregateRepository",
            "risk-assessment_Service",
            "risk-assessment_ApiController",
            "risk-assessment_AggregateRepository");

  }

}
