package blueprint.externalsystems.creditbureau.rest.config;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import blueprint.externalsystems.creditbureau.api.client.ScoreRequestsApi;
import blueprint.externalsystems.creditbureau.callback.server.ScoreCallbackApi;
import blueprint.externalsystems.creditbureau.rest.CreditBureauRestAdapter;
import blueprint.workflowmodule.loanapproval.CreditBureau;
import io.quarkus.runtime.Startup;
import io.vanillabp.cockpit.support.rest.restclient.RestClients;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link CreditBureau} bean and the callback endpoint, and does nothing
 * else.
 *
 * <p>
 * Quarkus finds this class, and the endpoint, through the index the build of this module writes.
 * </p>
 */
@ApplicationScoped
public class CreditBureauRestAdapterProducer {

  /** Where the keys of the client are, for example {@code credit-bureau.client.base-url}. */
  public static final String KEYS = "credit-bureau.client";

  /** The path of the callback endpoint, as the generated interface declares it. */
  private static final String CALLBACK_PATH = ScoreCallbackApi.class
      .getAnnotation(Path.class)
      .value();

  /**
   * The credit bureau, over REST. The support library of the Business Cockpit builds the
   * generated client from the keys below {@value #KEYS}: {@code base-url}, the timeouts, a
   * proxy, retries and the rest. It is created while the application starts, so a missing
   * address or callback address stops the start with a message instead of failing the first loan
   * approval.
   *
   * @param restClients The builder of the support library.
   * @param callbackUrl The value of {@code credit-bureau.callback-url}: the address of this
   *                    application's callback endpoint, as the credit bureau reaches it. Behind a
   *                    proxy that is not the address the application sees itself, so it is
   *                    configured, not guessed. Optional here only so the adapter can say itself
   *                    what is missing.
   * @return The adapter.
   */
  @Produces
  @Singleton
  @Startup
  public CreditBureau creditBureau(
      final RestClients restClients,
      @ConfigProperty(name = "credit-bureau.callback-url") final Optional<String> callbackUrl) {

    final var callbackAddress = callbackUrl
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The credit bureau does not know where to send a score. Set \
            'credit-bureau.callback-url' to the address of this application followed by '%s', \
            as the credit bureau reaches it. For a local run that is http://localhost:8080%s."""
            .formatted(
                CALLBACK_PATH,
                CALLBACK_PATH)));

    return new CreditBureauRestAdapter(restClients.build(ScoreRequestsApi.class, KEYS), callbackAddress);

  }

}
