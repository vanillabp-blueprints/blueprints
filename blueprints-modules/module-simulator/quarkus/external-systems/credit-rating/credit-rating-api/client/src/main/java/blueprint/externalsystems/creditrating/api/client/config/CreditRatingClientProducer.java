package blueprint.externalsystems.creditrating.api.client.config;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import blueprint.externalsystems.creditrating.api.client.CreditRatingApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * The client module wires its client. An application which has this JAR on its classpath sets
 * {@code credit-rating.base-url} and injects the generated interface. It writes no client
 * configuration of its own. Created while the application starts, so a missing address stops the
 * start with a message.
 *
 * <p>
 * Quarkus finds this class through the index the build of this module writes.
 * </p>
 */
@ApplicationScoped
public class CreditRatingClientProducer {

  /**
   * The client of {@link CreditRatingApi}.
   *
   * @param properties Where the credit rating is.
   * @return The client.
   */
  @Produces
  @Singleton
  @Startup
  public CreditRatingApi creditRatingApi(
      final CreditRatingClientProperties properties) {

    return build(CreditRatingApi.class, properties);

  }

  private static <T> T build(
      final Class<T> api,
      final CreditRatingClientProperties properties) {

    final var baseUrl = properties
        .baseUrl()
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The credit rating has no address. Set 'credit-rating.base-url' to where it answers. \
            For the simulator of this blueprint that is http://localhost:8079/credit-rating, and \
            the profile 'local' of the application sets it."""));

    // The generated client is an interface. The REST client of Quarkus implements it.
    return QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(URI.create(baseUrl))
        .connectTimeout(properties.connectTimeout(), TimeUnit.MILLISECONDS)
        .readTimeout(properties.readTimeout(), TimeUnit.MILLISECONDS)
        .build(api);

  }

}
