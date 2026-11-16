package blueprint.testdriver.loanapproval.steps;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.simulator.SimulatorTestApi;

/**
 * The customer signs. The simulated e-signature service calls the application back, as the real
 * one does. The step before made sure that the contract is at the service, because the simulator
 * cannot sign a contract it never got.
 */
@Component
public class ContractSignedStep implements Step {

  @Autowired
  private SimulatorTestApi simulator;

  @Autowired
  private StepSupport support;

  /**
   * The simulator can only sign a contract which it got.
   */
  @Override
  public List<Class<? extends Step>> prerequisites() {

    return List.of(ContractCheckOpenedStep.class);

  }

  @Override
  public String description() {

    return "the customer signs the contract";

  }

  @Override
  public void run(
      final Scenario scenario) {

    final var answer = simulator.sign(scenario.getLoanRequestId());
    if (!answer.startsWith("200")) {
      throw new ProcessStateNotReachedException(scenario, this, "the simulated e-signature service answered '"
          + answer
          + "'.");
    }

    support.awaitLoanApproval(
        scenario,
        this,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSigned()),
        loanRequest -> "the simulator signed and answered '"
            + answer
            + "', but the application did not take the signature. Check 'e-signature.callback-url'.");

  }

}
