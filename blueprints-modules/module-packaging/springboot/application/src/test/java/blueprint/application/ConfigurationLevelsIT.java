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
 * properties - then {@code application.yaml} of the assembling application, then the module's
 * own file. A module's file is a set of DEFAULTS, and the application which collects the
 * module may overrule them; only the module's profile-specific file beats its plain one.
 * </p>
 *
 * <p>
 * It was the other way round until 2026-08-21, when the framework turned it around
 * (`adapter-platform-integration`, story 101). Worth knowing when reading older projects: an
 * application which could not talk a module out of a value can do so now.
 * </p>
 */
@SpringBootTest
public class ConfigurationLevelsIT {

  @Autowired
  private LoanApprovalProperties properties;

  @Test
  public void theApplicationWinsOverTheModulesOwnFile() {

    assertThat(properties.getRatingScale())
        .describedAs(
            "the module's own file says 100, 'application.yaml' of this application says 42."
                + " The application wins: a module's file carries defaults, and whoever"
                + " assembles the module decides what its environment needs.")
        .isEqualTo(42);

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
