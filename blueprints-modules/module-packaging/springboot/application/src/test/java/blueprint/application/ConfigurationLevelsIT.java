package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.acme.loanapproval.config.LoanApprovalProperties;

/**
 * Three places set the configuration of a workflow module, and this test says which of them
 * arrives. It matters most in an application which assembles modules it does not own: the
 * values are somebody else's, and guessing wrong about the order is how a deployment ends up
 * running with a default nobody meant.
 *
 * <p>
 * The order, highest first: what comes from outside - environment variables, system
 * properties - then the module's own file, then {@code application.yaml}. That the module
 * wins over the application is the surprising one, and it is deliberate: a module knows its
 * own defaults better than the runtime collecting it does.
 * </p>
 */
@SpringBootTest
public class ConfigurationLevelsIT {

  @Autowired
  private LoanApprovalProperties properties;

  @Test
  public void theModulesOwnFileWinsOverTheApplication() {

    assertThat(properties.getRatingScale())
        .describedAs(
            "'application.yaml' says 42, the module's own file says 100."
                + " A module's file wins - the application cannot talk a module out of its defaults.")
        .isEqualTo(100);

  }

  @Test
  public void whatComesFromOutsideWinsOverBoth() {

    assertThat(properties.getRatingProvider())
        .describedAs(
            "the module says 'internal', 'application.yaml' says 'from-the-application',"
                + " and the deployment sets a system property. The deployment wins,"
                + " which is where a value an environment really determines belongs.")
        .isEqualTo("from-the-environment");

  }

}
