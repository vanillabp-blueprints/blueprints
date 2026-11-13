package blueprint.simulator.esignature;

/**
 * A document handed over for signing, in the words of the e-signature service.
 *
 * @param contractId  The id the caller gave the document.
 * @param signer      Who has to sign.
 * @param document    What is signed.
 * @param callbackUrl Where the service reports the signature.
 */
public record Envelope(
                       String contractId,
                       String signer,
                       String document,
                       String callbackUrl) {
}
