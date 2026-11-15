package blueprint.externalsystems.esignature;

/**
 * Having a contract signed, as the application sees it: the application hands over a contract,
 * and the signature comes back later, when the customer signed.
 *
 * <p>
 * The interface is named after the role the system plays, not after the product which plays it
 * today. The answer does not come back through this interface. The adapter receives it and
 * hands it to {@link SignedContracts}, which the application implements.
 * </p>
 */
public interface ContractSigning {

  /**
   * Hands a contract over for signing. The method returns when the e-signature service took the
   * contract, not when it was signed.
   *
   * @param contract The contract to be signed.
   * @throws ContractSigningUnavailableException If the e-signature service does not answer.
   */
  void sendForSigning(
      ContractToSign contract);

}
