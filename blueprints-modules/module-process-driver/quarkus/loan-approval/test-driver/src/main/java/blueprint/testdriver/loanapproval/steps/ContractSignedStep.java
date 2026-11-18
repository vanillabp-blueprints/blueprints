package blueprint.testdriver.loanapproval.steps;

import java.util.List;

import blueprint.testdriver.loanapproval.Scenario;
import blueprint.testdriver.loanapproval.TestData;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The customer signs. The step chooses {@link TestData#JOHN_SIGNING} as the customer, before the
 * loan is requested. The simulated e-signature service signs his contract a moment after it got
 * it, and calls the application back, as the real one does when a person signs. The step only
 * waits for the signature.
 */
@Singleton
@IfBuildProperty(name = "process-driver.enabled", stringValue = "true")
public class ContractSignedStep implements Step {

  @Inject
  StepSupport support;

  /**
   * The signature comes for a contract which went out, and the test checks it in the open task.
   */
  @Override
  public List<Class<? extends Step>> prerequisites() {

    return List.of(ContractCheckOpenedStep.class);

  }

  /**
   * A signed contract needs a customer who signs by himself.
   */
  @Override
  public TestData prepare(
      final TestData testData) {

    return testData.withCustomerName(TestData.JOHN_SIGNING);

  }

  @Override
  public String description() {

    return "the customer signs the contract";

  }

  @Override
  public void run(
      final Scenario scenario) {

    support.awaitLoanRequest(
        scenario,
        this,
        loanRequest -> Boolean.TRUE.equals(loanRequest.getContractSigned()),
        loanRequest -> "the simulated e-signature service did not report the signature of '"
            + scenario.getTestData().customerName()
            + "', or the application did not take it. Look into the log of the simulator for"
            + " 'SIGNED', and check 'e-signature.callback-url'.");

  }

}
