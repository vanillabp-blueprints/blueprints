package blueprint.externalsystems.esignature;

/**
 * A contract handed over for signing, in the types of the application.
 *
 * @param contractId The id the application gives the contract. The signature comes back with it.
 * @param signer     The id of the customer who has to sign.
 * @param text       What the customer signs.
 */
public record ContractToSign(
                             String contractId,
                             String signer,
                             String text) {
}
