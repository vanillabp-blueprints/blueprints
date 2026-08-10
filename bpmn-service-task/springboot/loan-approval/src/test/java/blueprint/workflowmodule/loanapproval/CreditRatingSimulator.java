package blueprint.workflowmodule.loanapproval;

import java.util.concurrent.atomic.AtomicInteger;

import blueprint.workflowmodule.Simulator;

/**
 * The rating provider, as far as the test is concerned. It stands in for the surrounding
 * system so the workflow module can be run without one, and it lets a test decide how that
 * system behaves: answering, or refusing to.
 *
 * @see Simulator
 */
public class CreditRatingSimulator extends Simulator implements CreditRatingClient {

  private final AtomicInteger requestsToFail = new AtomicInteger();

  /**
   * Makes the provider refuse the next requests, which is how the test provokes the retry
   * of the BPMS.
   *
   * @param count How many requests are refused before the provider answers again.
   */
  public void failNextRequests(
      final int count) {

    requestsToFail.set(count);

  }

  @Override
  public int rate(
      final String provider,
      final String loanRequestId,
      final int amount) {

    record("rate "
        + loanRequestId);

    if (requestsToFail.getAndUpdate(left -> left > 0 ? left - 1 : 0) > 0) {

      throw new CreditRatingUnavailableException(provider);

    }

    return Math.min(100, amount / 100);

  }

  @Override
  public void reset() {

    super.reset();
    requestsToFail.set(0);

  }

}
