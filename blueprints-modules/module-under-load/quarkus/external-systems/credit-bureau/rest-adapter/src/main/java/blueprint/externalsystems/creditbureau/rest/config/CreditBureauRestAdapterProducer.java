package blueprint.externalsystems.creditbureau.rest.config;

import blueprint.externalsystems.creditbureau.api.client.ScoreRequestsApi;
import blueprint.externalsystems.creditbureau.api.client.config.CreditBureauClientProperties;
import blueprint.externalsystems.creditbureau.callback.server.ScoreCallbackApi;
import blueprint.externalsystems.creditbureau.rest.CreditBureauRestAdapter;
import blueprint.workflowmodule.loanapproval.CreditBureau;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link CreditBureau} bean and the callback endpoint, and does nothing
 * else. The client comes from the client module of the API, which builds it out of the
 * properties {@code credit-bureau.*}.
 *
 * <p>
 * Quarkus finds this class, and the endpoint, through the index the build of this module writes.
 * </p>
 */
@ApplicationScoped
public class CreditBureauRestAdapterProducer {

  /** The path of the callback endpoint, as the generated interface declares it. */
  private static final String CALLBACK_PATH = ScoreCallbackApi.class
      .getAnnotation(Path.class)
      .value();

  /**
   * The credit bureau, over REST. Created while the application starts, so a missing callback
   * address stops the start with a message instead of failing the first loan approval.
   *
   * @param client           The generated client, built by the client module.
   * @param clientProperties Where the credit bureau is, for the messages of the adapter.
   * @param properties       Where the credit bureau calls back.
   * @return The adapter.
   */
  @Produces
  @Singleton
  @Startup
  public CreditBureau creditBureau(
      final ScoreRequestsApi client,
      final CreditBureauClientProperties clientProperties,
      final CreditBureauProperties properties) {

    final var callbackUrl = properties
        .callbackUrl()
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The credit bureau does not know where to send a score. Set \
            'credit-bureau.callback-url' to the address of this application followed by '%s', \
            as the credit bureau reaches it. For a local run that is http://localhost:8080%s."""
            .formatted(
                CALLBACK_PATH,
                CALLBACK_PATH)));

    return new CreditBureauRestAdapter(client, clientProperties.baseUrl().orElseThrow(), callbackUrl);

  }

}
