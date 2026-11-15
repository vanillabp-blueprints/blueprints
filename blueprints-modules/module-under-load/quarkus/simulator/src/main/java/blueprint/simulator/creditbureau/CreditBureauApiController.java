package blueprint.simulator.creditbureau;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import blueprint.externalsystems.creditbureau.api.server.ScoreRequestsApi;
import blueprint.externalsystems.creditbureau.api.server.model.ScoreRequest;
import blueprint.externalsystems.creditbureau.callback.client.ScoreCallbackApi;
import blueprint.externalsystems.creditbureau.callback.client.model.Score;
import blueprint.simulator.loadrun.LoadProfile;
import blueprint.simulator.loadrun.Spread;
import blueprint.simulator.loadrun.Threads;
import jakarta.annotation.PreDestroy;

/**
 * Plays the credit bureau: it takes a question, and answers it after a latency, and sometimes
 * not at all, as the load profile says.
 *
 * <p>
 * This is the neighbour system of the workflow module. The application talks to it through
 * its REST adapter, exactly as it would talk to the real bureau. The path, the method and the
 * JSON come from {@link ScoreRequestsApi}, the interface generated from
 * {@code external-systems/credit-bureau/credit-bureau-api/credit-bureau.yaml}. The answer goes
 * to the callback address of the question, with the client generated from
 * {@code credit-bureau-callback.yaml}.
 * </p>
 */
@RestController
public class CreditBureauApiController implements ScoreRequestsApi {

  private static final Logger log = LoggerFactory.getLogger(CreditBureauApiController.class);

  /** The element of the process which waits for the answer. The profile is keyed by it. */
  static final String ANSWER_ELEMENT = "CatchEvent_CreditScoreReceived";

  private final LoadProfile.Neighbour bureau;

  private final RestClient.Builder restClients;

  /** One client per address to call back, made when the address is first seen. */
  private final Map<String, ScoreCallbackApi> callbacks = new ConcurrentHashMap<>();

  private final ScheduledExecutorService answers = Executors.newScheduledThreadPool(
      4,
      Threads.named("credit-bureau"));

  /**
   * Creates the controller.
   *
   * @param profile     The load profile which says how the bureau behaves.
   * @param restClients The REST client builder of Spring Boot, for the callbacks.
   */
  public CreditBureauApiController(
      final LoadProfile profile,
      final RestClient.Builder restClients) {

    this.bureau = profile.neighbourOf(ANSWER_ELEMENT);
    this.restClients = restClients;

  }

  /**
   * Takes a question, and answers it later.
   *
   * @param question The loan request, its amount and where to send the score.
   * @return 202, because the score comes later.
   */
  @Override
  public ResponseEntity<Void> requestScore(
      final ScoreRequest question) {

    if (ThreadLocalRandom
        .current()
        .nextDouble() >= bureau.noAnswerShare()) {

      final var latency = Spread.around(bureau.latency(), bureau.latencySpread());
      answers.schedule(
          () -> answer(question),
          latency.toMillis(),
          TimeUnit.MILLISECONDS);

    }

    return ResponseEntity
        .accepted()
        .build();

  }

  /** Stops answering. Questions asked afterwards stay without an answer. */
  @PreDestroy
  public void stop() {

    answers.shutdownNow();

  }

  private void answer(
      final ScoreRequest question) {

    try {
      callbackTo(question.callbackUrl()).reportScore(new Score(
          question.loanRequestId(), Math.min(100, question.amount() / 500)));
    } catch (final RuntimeException e) {
      log.warn("The {} could not deliver its answer: {}", bureau.name(), e.toString());
    }

  }

  /**
   * The client of the callback API at an address. The address ends with the path of that API,
   * so the client's base address is what comes before it.
   */
  private ScoreCallbackApi callbackTo(
      final String callbackUrl) {

    final var baseUrl = callbackUrl.endsWith(ScoreCallbackApi.PATH_REPORT_SCORE)
        ? callbackUrl.substring(0, callbackUrl.length() - ScoreCallbackApi.PATH_REPORT_SCORE.length())
        : callbackUrl;
    return callbacks.computeIfAbsent(
        baseUrl,
        address -> HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClients
                .clone()
                .baseUrl(address)
                .build()))
            .build()
            .createClient(ScoreCallbackApi.class));

  }

}
