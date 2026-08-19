package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * A workflow module carries its configuration per environment, and this test is the proof:
 * the value from {@code loan-approval/loan-approval-test.yaml} arrives rather than the one
 * from {@code loan-approval/loan-approval.yaml}.
 *
 * <p>
 * Nothing had to be set up for that. A test runs in the profile {@code test}, and a module's
 * file for an active profile applies on top of its base file. The BPMS profile of the build
 * survives it, because that one is the parent profile.
 * </p>
 *
 * <p>
 * Worth knowing, and the reason this test exists: the profile lives in the file NAME. A
 * module's configuration file is not a place to switch on a profile from the inside.
 * </p>
 */
@QuarkusTest
public class ModuleConfigurationPerProfileIT {

  @Inject
  LoanApprovalProperties properties;

  @Test
  public void theProfileFileOfTheModuleWins() {

    assertThat(properties.ratingProvider())
        .describedAs(
            "Expected the value of 'loan-approval-test.yaml'. Getting 'internal' means only"
                + " 'loan-approval.yaml' was read - check the file name, it carries the profile.")
        .isEqualTo("sandbox");

    assertThat(properties.ratingScale())
        .describedAs("What the profile file does not say stays as the module's base file has it.")
        .isEqualTo(100);

  }

}
