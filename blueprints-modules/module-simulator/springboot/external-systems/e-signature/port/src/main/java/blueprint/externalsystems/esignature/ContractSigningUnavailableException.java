package blueprint.externalsystems.esignature;

/**
 * The e-signature service does not answer, or it answers with an error of its own. The exception
 * names the role of the system, so an application can tell which system is missing without
 * knowing the product behind it.
 */
public class ContractSigningUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The role of the system, as an application shows it to its user. */
  public static final String ROLE = "e-signature";

  /**
   * Creates the exception.
   *
   * @param message What went wrong.
   * @param cause   The error of the technology the adapter uses.
   */
  public ContractSigningUnavailableException(
      final String message,
      final Throwable cause) {

    super(message, cause);

  }

}
