package blueprint.simulator.esignature;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
import blueprint.simulator.testapi.server.model.SignatureStatus;

/**
 * Sends the reports of the simulated e-signature service to the callback address of a document,
 * as the real service does when the signer signed or declined. Every report is noted in
 * {@link Envelopes}, together with the answer of the application, so a test can see it.
 */
@Component
public class SignatureReports {

  private static final Logger log = LoggerFactory.getLogger(SignatureReports.class);

  private final Envelopes envelopes;

  private final RestClient.Builder restClients;

  /**
   * Creates the component.
   *
   * @param envelopes   What the simulated service remembers.
   * @param restClients The REST client builder of Spring Boot, for the callbacks.
   */
  public SignatureReports(
      final Envelopes envelopes,
      final RestClient.Builder restClients) {

    this.envelopes = envelopes;
    this.restClients = restClients;

  }

  /**
   * Sends a report a moment from now, as a signer does who acts on their own. Nobody waits for
   * the answer, so a report which cannot be delivered is only logged.
   *
   * @param contractId  The id of the document.
   * @param status      What the report says.
   * @param callbackUrl Where to send it.
   */
  public void sendInAMoment(
      final String contractId,
      final SignatureStatus status,
      final String callbackUrl) {

    CompletableFuture.runAsync(() -> {
      try {
        send(contractId, status, callbackUrl);
      } catch (CallbackNotDeliveredException e) {
        log.warn("{}", e.getMessage());
      }
    }, CompletableFuture.delayedExecutor(SignerBehaviour.MOMENT.toMillis(), TimeUnit.MILLISECONDS));

  }

  /**
   * Sends a report now and waits for the answer of the application.
   *
   * @param contractId  The id of the document.
   * @param status      What the report says.
   * @param callbackUrl Where to send it. It ends with the path of the callback API.
   * @return The report and the status the application answered with.
   * @throws CallbackNotDeliveredException If the address does not end with the path of the
   *                                       callback API, or if nobody answers there.
   */
  public SentCallback send(
      final String contractId,
      final SignatureStatus status,
      final String callbackUrl) {

    if (!callbackUrl.endsWith(SignatureCallbackApi.PATH_REPORT_SIGNATURE)) {
      throw new CallbackNotDeliveredException("Contract '"
          + contractId
          + "' "
          + status
          + ", but the callback address "
          + callbackUrl
          + " does not end with "
          + SignatureCallbackApi.PATH_REPORT_SIGNATURE
          + ", the path of the callback API");
    }

    Integer answer;
    try {

      answer = callbackTo(callbackUrl)
          .reportSignature(new Callback(contractId, Callback.StatusEnum.valueOf(status.name())))
          .getStatusCode()
          .value();

    } catch (RestClientResponseException e) {

      // The application answered, but with an error, for example 404 for an unknown contract.
      answer = e
          .getStatusCode()
          .value();

    } catch (ResourceAccessException e) {

      envelopes.sent(new SentCallback(contractId, status, null));
      throw new CallbackNotDeliveredException("Contract '"
          + contractId
          + "' "
          + status
          + ", but nobody answers at "
          + callbackUrl);

    }

    final var sent = new SentCallback(contractId, status, answer);
    envelopes.sent(sent);

    log.info(
        "Contract '{}' {}, reported to {}, which answered {}",
        contractId,
        status,
        callbackUrl,
        answer);

    return sent;

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
