package blueprint.externalsystems.creditbureau.api.client.config;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import blueprint.externalsystems.creditbureau.api.client.ScoreRequestsApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * The client module wires its client. An application which has this JAR on its classpath sets
 * {@code credit-bureau.base-url} and injects the generated interface. It writes no client
 * configuration of its own. Created while the application starts, so a missing address stops the
 * start with a message.
 *
 * <p>
 * Quarkus finds this class through the index the build of this module writes.
 * </p>
 */
@ApplicationScoped
public class CreditBureauClientProducer {

  /**
   * The client of {@link ScoreRequestsApi}.
   *
   * @param properties Where the credit bureau is.
   * @return The client.
   */
  @Produces
  @Singleton
  @Startup
  public ScoreRequestsApi scoreRequestsApi(
      final CreditBureauClientProperties properties) {

    return build(ScoreRequestsApi.class, properties);

  }

  private static <T> T build(
      final Class<T> api,
      final CreditBureauClientProperties properties) {

    final var baseUrl = properties
        .baseUrl()
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The credit bureau has no address. Set 'credit-bureau.base-url' to where it answers. \
            For the simulator of this blueprint that is http://localhost:8079/credit-bureau, and \
            'application.yaml' sets it."""));

    // The generated client is an interface. The REST client of Quarkus implements it.
    return QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(URI.create(baseUrl))
        .connectTimeout(properties.connectTimeout(), TimeUnit.MILLISECONDS)
        .readTimeout(properties.readTimeout(), TimeUnit.MILLISECONDS)
        .build(api);

  }

}
