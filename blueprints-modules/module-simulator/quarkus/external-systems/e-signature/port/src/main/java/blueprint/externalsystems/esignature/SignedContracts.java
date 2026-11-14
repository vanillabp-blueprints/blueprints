package blueprint.externalsystems.esignature;

/**
 * The way back from the e-signature service into the application. The application implements
 * this interface, and the adapter calls it when the e-signature service reports a signature.
 *
 * <p>
 * So the direction of the call is reversed, but the direction of the dependency is not: the
 * adapter knows this interface, and the application still knows nothing about the adapter.
 * </p>
 */
public interface SignedContracts {

  /**
   * A contract was signed.
   *
   * @param contractId The id the application gave the contract when it handed it over.
   * @return {@code false} if the application does not know a contract of this id.
   */
  boolean signed(
      String contractId);

}
