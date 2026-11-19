package blueprint.testdriver.loanapproval;

import lombok.Getter;
import lombok.Setter;

/**
 * One loan request on its way to a {@link ProcessState}. The steps fill it in as they go, and
 * {@link ProcessDriver#advanceTo(ProcessState, TestData)} returns it, so the caller knows which
 * loan approval it got.
 */
@Getter
public class Scenario {

  private final ProcessState state;

  private final TestData testData;

  /** The customer the driver reserved at the simulated credit rating. */
  @Setter
  private String customerId;

  /** The id of the loan request, which is the id of the workflow aggregate. */
  @Setter
  private String loanRequestId;

  Scenario(
      final ProcessState state,
      final TestData testData) {

    this.state = state;
    this.testData = testData;

  }

}
