package blueprint.workflowmodule.loanapproval;

/**
 * The rating provider did not answer. This is a technical failure, so the task handler
 * does not catch it: the transaction is rolled back and the BPMS delivers the task again.
 *
 * <p>
 * Contrast it with {@link io.vanillabp.spi.service.TaskException}, which is not a failure
 * but a business outcome the BPMN reacts to.
 * </p>
 */
public class CreditRatingUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CreditRatingUnavailableException(
      final String provider) {

    super("The rating provider '"
        + provider
        + "' did not answer");

  }

}
