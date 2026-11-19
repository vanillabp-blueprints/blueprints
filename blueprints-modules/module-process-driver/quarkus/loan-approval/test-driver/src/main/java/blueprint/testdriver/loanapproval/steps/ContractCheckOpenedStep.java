package blueprint.testdriver.loanapproval.steps;

import java.util.List;

import blueprint.testdriver.loanapproval.Scenario;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Waits until the contract went to the e-signature service and the user task "Check contract"
 * is open. The code of the workflow writes both, after the engine moved on. So when this
 * step returns, nothing of the steps before is still on its way.
 */
@Singleton
@IfBuildProperty(name = "process-driver.enabled", stringValue = "true")
public class ContractCheckOpenedStep implements Step {

  @Inject
  StepSupport support;

  /**
   * The contract goes out only after the risk is assessed, if the amount needed it.
   */
  @Override
  public List<Class<? extends Step>> prerequisites() {

    return List.of(RiskAssessedOnTheWayStep.class);

  }

  @Override
  public String description() {

    return "the contract goes out and the user task 'Check contract' opens";

  }

  @Override
  public void run(
      final Scenario scenario) {

    support.awaitLoanRequest(
        scenario,
        this,
        loanRequest -> Boolean.TRUE
            .equals(loanRequest.getContractSentForSigning()) && loanRequest.getCheckContractTaskId() != null,
        loanRequest -> {
          if (loanRequest.getAssessRiskTaskId() != null) {
            return "the workflow waits in the user task 'Assess risk'. Nobody completed it.";
          }
          if (!Boolean.TRUE.equals(loanRequest.getContractSentForSigning())) {
            return "the contract was not sent to the e-signature service. Look into the log of the"
                + " simulator for the call to '/e-signature', and into the log of the application"
                + " for an error of the service task 'Send contract for signing'.";
          }
          return "the contract went out, but the workflow did not open the user task 'Check contract'.";
        });

  }

}
