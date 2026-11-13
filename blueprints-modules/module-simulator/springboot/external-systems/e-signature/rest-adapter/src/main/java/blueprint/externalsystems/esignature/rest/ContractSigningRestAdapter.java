package blueprint.externalsystems.esignature.rest;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import blueprint.externalsystems.esignature.ContractSigning;
import blueprint.externalsystems.esignature.ContractSigningUnavailableException;
import blueprint.externalsystems.esignature.ContractToSign;

/**
 * The e-signature service, reached over REST. The interface it speaks is described in
 * {@code external-systems/e-signature/openapi.yaml} of this blueprint.
 *
 * <p>
 * The service answers in two steps. This class hands the contract over and tells the service
 * where to report the signature. The report arrives later at {@link CallbackController}, which
 * belongs to this adapter as well. Both halves together are what the port promises.
 * </p>
 */
public class ContractSigningRestAdapter implements ContractSigning {

  /** A contract handed over, in the words of the e-signature service. Only this adapter sees it. */
  record Envelope(
                  String contractId,
                  String signer,
                  String document,
                  String callbackUrl) {
  }

  private final RestClient eSignature;

  private final String baseUrl;

  private final String callbackUrl;

  /**
   * Creates the adapter.
   *
   * @param eSignature  A REST client whose base address is the e-signature service.
   * @param baseUrl     The same address, for the messages of this class.
   * @param callbackUrl Where the e-signature service reports a signature: the address of
   *                    {@link CallbackController}, as the service reaches it.
   */
  public ContractSigningRestAdapter(
      final RestClient eSignature,
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

      eSignature
          .post()
          .uri("/api/v1/envelopes")
          .body(new Envelope(contract.contractId(), contract.signer(), contract.text(), callbackUrl))
          .retrieve()
          .toBodilessEntity();

    } catch (ResourceAccessException | RestClientResponseException e) {

      throw new ContractSigningUnavailableException("The e-signature service at '"
          + baseUrl
          + "' did not take contract '"
          + contract.contractId()
          + "'", e);

    }

  }

}
