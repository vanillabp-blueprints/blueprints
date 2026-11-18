package blueprint.simulator.esignature;

/**
 * The e-signature service could not deliver a report: the callback address is wrong, or nobody
 * answers there. The test API answers with 502 and this message.
 */
public class CallbackNotDeliveredException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message What went wrong, for the caller of the test API.
   */
  public CallbackNotDeliveredException(
      final String message) {

    super(message);

  }

}
