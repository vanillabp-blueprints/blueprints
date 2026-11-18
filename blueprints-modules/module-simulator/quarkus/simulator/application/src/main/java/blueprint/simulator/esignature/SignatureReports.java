package blueprint.simulator.esignature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import blueprint.externalsystems.esignature.callback.client.SignatureCallbackApi;
import blueprint.externalsystems.esignature.callback.client.model.Callback;
import blueprint.simulator.testapi.server.model.SentCallback;

/**
 * Sends the reports of the simulated e-signature service to a callback address, as the real
 * service does when the signer signed.
 */
@Component
public class SignatureReports {

  private static final Logger log = LoggerFactory.getLogger(SignatureReports.class);

  private final RestClient.Builder restClients;

  /**
   * Creates the component.
   *
   * @param restClients The REST client builder of Spring Boot, for the callbacks.
   */
  public SignatureReports(
      final RestClient.Builder restClients) {

    this.restClients = restClients;

  }

  /**
   * Reports that a document was signed, and waits for the answer of the application.
   *
   * @param contractId  The id of the document.
   * @param callbackUrl Where to send the report. It ends with the path of the callback API.
   * @return The report and the status the application answered with.
   * @throws CallbackNotDeliveredException If the address does not end with the path of the
   *                                       callback API, or if nobody answers there.
   */
  public SentCallback sendSigned(
      final String contractId,
      final String callbackUrl) {

    if (!callbackUrl.endsWith(SignatureCallbackApi.PATH_REPORT_SIGNATURE)) {
      throw new CallbackNotDeliveredException("Contract '"
          + contractId
          + "' signed, but its callback address "
          + callbackUrl
          + " does not end with "
          + SignatureCallbackApi.PATH_REPORT_SIGNATURE
          + ", the path of the callback API");
    }

    int answer;
    try {

      answer = callbackTo(callbackUrl)
          .reportSignature(new Callback(contractId, Callback.StatusEnum.SIGNED))
          .getStatusCode()
          .value();

    } catch (RestClientResponseException e) {

      // The application answered, but with an error, for example 404 for an unknown contract.
      answer = e
          .getStatusCode()
          .value();

    } catch (ResourceAccessException e) {

      log.warn(
          "Contract '{}' signed, but nobody answers at {}",
          contractId,
          callbackUrl);
      throw new CallbackNotDeliveredException("Contract '"
          + contractId
          + "' signed, but nobody answers at "
          + callbackUrl);

    }

    log.info(
        "Contract '{}' signed, reported to {}, which answered {}",
        contractId,
        callbackUrl,
        answer);

    return new SentCallback(contractId, answer);

  }

  /**
   * The generated client of the callback API, aimed at one callback address.
   *
   * <p>
   * The envelope carries the whole address of the callback. The generated client adds the path
   * of the callback API itself, so it gets the address without that path as its base.
   * </p>
   *
   * @param callbackUrl The callback address of an envelope. It ends with the path of the API.
   * @return The client.
   */
  private SignatureCallbackApi callbackTo(
      final String callbackUrl) {

    final var baseUrl = callbackUrl.substring(
        0,
        callbackUrl.length() - SignatureCallbackApi.PATH_REPORT_SIGNATURE.length());

    // The builder of Spring Boot is shared by every call, so each callback gets a copy of it.
    final var restClient = restClients
        .clone()
        .baseUrl(baseUrl)
        .build();

    return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(SignatureCallbackApi.class);

  }

}
