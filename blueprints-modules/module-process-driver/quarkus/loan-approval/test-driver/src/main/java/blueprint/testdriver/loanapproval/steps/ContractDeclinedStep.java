package blueprint.testdriver.loanapproval.steps;

import java.util.List;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.TestData;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The customer declines to sign, and the loan is declined. The step chooses
 * {@link TestData#JACK_NOSIGN} as the customer, before the loan is requested. The simulated
 * e-signature service reports his decline a moment after it got the contract, as the real one
 * does when a person declines. The workflow takes the task "Check contract" away and ends. The
 * step only waits for that.
 *
 * <p>
 * It does not come after {@link ContractCheckOpenedStep}. That step waits until the task "Check
 * contract" is open, but the decline may take the task away before the driver sees it. So this
 * step waits for the end itself, which the module writes when the workflow took the task away.
 * </p>
 */
@Singleton
@IfBuildProperty(name = "process-driver.enabled", stringValue = "true")
public class ContractDeclinedStep implements Step {

  @Inject
  StepSupport support;

  /**
   * The contract goes out only after the risk is assessed, if the amount needed it.
   */
  @Override
  public List<Class<? extends Step>> prerequisites() {

    return List.of(RiskAssessedOnTheWayStep.class);

  }

  /**
   * A declined contract needs a customer who declines by himself.
   */
  @Override
  public TestData prepare(
      final TestData testData) {

    return testData.withCustomerName(TestData.JACK_NOSIGN);

  }

  @Override
  public String description() {

    return "the customer declines to sign the contract, and the loan is declined";

  }

  @Override
  public void run(
      final Scenario scenario) {

    support.awaitLoanRequest(
        scenario,
        this,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getLoanDeclined()),
        loanRequest -> {
          if (loanRequest.getAssessRiskTaskId() != null) {
            return "the workflow waits in the user task 'Assess risk'. Nobody completed it.";
          }
          if (!Boolean.TRUE.equals(loanRequest.getContractSentForSigning())) {
            return "the contract was not sent to the e-signature service. Look into the log of the"
                + " application for an error of the service task 'Send contract for signing'.";
          }
          if (!Boolean.TRUE.equals(loanRequest.getContractDeclined())) {
            return "the simulated e-signature service did not report the decline of '"
                + scenario.getTestData().customerName()
                + "', or the application did not take it. Look into the log of the simulator for"
                + " 'DECLINED', and check 'e-signature.callback-url'.";
          }
          return "the application took the decline, but the workflow did not take the task 'Check"
              + " contract' away. Look into the log of the application for the message"
              + " 'ContractDeclined'.";
        });

  }

}
