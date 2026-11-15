package blueprint.externalsystems.esignature.rest;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import blueprint.externalsystems.esignature.ContractSigning;
import blueprint.externalsystems.esignature.ContractSigningUnavailableException;
import blueprint.externalsystems.esignature.ContractToSign;
import blueprint.externalsystems.esignature.api.client.EnvelopesApi;
import blueprint.externalsystems.esignature.api.client.model.Envelope;

/**
 * The e-signature service, reached over REST. The API is described in
 * {@code external-systems/e-signature/e-signature-api/e-signature.yaml} of this blueprint, and
 * {@link EnvelopesApi} is the client generated from it.
 *
 * <p>
 * The service answers in two steps. This class hands the contract over and tells the service
 * where to report the signature. The report arrives later at {@link CallbackController}, which
 * belongs to this adapter as well. Both halves together are what the port promises.
 * </p>
 */
public class ContractSigningRestAdapter implements ContractSigning {

  private final EnvelopesApi eSignature;

  private final String baseUrl;

  private final String callbackUrl;

  /**
   * Creates the adapter.
   *
   * @param eSignature  The generated client, whose base address is the e-signature service.
   * @param baseUrl     The same address, for the messages of this class.
   * @param callbackUrl Where the e-signature service reports a signature: the address of
   *                    {@link CallbackController}, as the service reaches it.
   */
  public ContractSigningRestAdapter(
      final EnvelopesApi eSignature,
      final String baseUrl,
      final String callbackUrl) {

    this.eSignature = eSignature;
    this.baseUrl = baseUrl;
    this.callbackUrl = callbackUrl;

  }

  @Override
  public void sendForSigning(
      final ContractToSign contract) {

    try {

      eSignature.createEnvelope(new Envelope(
          contract.contractId(), contract.signer(), contract.text(), callbackUrl));

    } catch (ResourceAccessException | RestClientResponseException e) {

      throw new ContractSigningUnavailableException("The e-signature service at '"
          + baseUrl
          + "' did not take contract '"
          + contract.contractId()
          + "'", e);

    }

  }

}
