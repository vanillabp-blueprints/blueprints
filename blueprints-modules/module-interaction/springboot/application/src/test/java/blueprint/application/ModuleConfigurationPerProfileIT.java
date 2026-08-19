package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;

/**
 * A workflow module carries its configuration per environment, and this test is the proof:
 * with the profile {@code test} active, the value from
 * {@code loan-approval/loan-approval-test.yaml} arrives rather than the one from
 * {@code loan-approval/loan-approval.yaml}.
 *
 * <p>
 * The profile is <em>included</em> rather than set, because the active profile already names
 * the BPMS this build runs against and that choice must survive. What the module files react
 * to is every active profile, so both apply. It is included for every test of this module,
 * in its POM: one set of properties means one application context, and two contexts in one
 * JVM mean two embedded engines competing for the jobs of one database.
 * </p>
 *
 * <p>
 * Worth knowing, and the reason this test exists: the profile lives in the file NAME.
 * Inside a workflow module's configuration file {@code spring.config.activate.on-profile}
 * has no effect, so a multi-document YAML would quietly deliver the wrong value.
 * </p>
 */
@SpringBootTest
public class ModuleConfigurationPerProfileIT {

  @Autowired
  private LoanApprovalProperties properties;

  @Test
  public void theProfileFileOfTheModuleWins() {

    assertThat(properties.getRatingProvider())
        .describedAs(
            "Expected the value of 'loan-approval-test.yaml'. Getting 'internal' means only"
                + " 'loan-approval.yaml' was read - check the file name, it carries the profile.")
        .isEqualTo("sandbox");

    assertThat(properties.getRatingScale())
        .describedAs("What the profile file does not say stays as the module's base file has it.")
        .isEqualTo(100);

  }

}
