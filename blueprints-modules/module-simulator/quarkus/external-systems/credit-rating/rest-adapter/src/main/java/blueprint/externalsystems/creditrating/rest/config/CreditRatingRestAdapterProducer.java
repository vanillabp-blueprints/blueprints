package blueprint.externalsystems.creditrating.rest.config;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.creditrating.api.client.CreditRatingApi;
import blueprint.externalsystems.creditrating.rest.CreditRatingRestAdapter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link CreditRating} bean and does nothing else.
 *
 * <p>
 * Quarkus finds this class through the index the build of this module writes.
 * </p>
 */
@ApplicationScoped
public class CreditRatingRestAdapterProducer {

  /**
   * The credit rating, over REST. Created while the application starts, so a missing address
   * stops the start with a message instead of failing the first loan request.
   *
   * @param properties Where the credit rating is.
   * @return The adapter.
   */
  @Produces
  @Singleton
  @Startup
  public CreditRating creditRating(
      final CreditRatingProperties properties) {

    final var baseUrl = properties
        .baseUrl()
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The credit rating has no address. Set 'credit-rating.base-url' to where it answers. \
            For the simulator of this blueprint that is http://localhost:8079/credit-rating, and \
            the profile 'local' of the application sets it."""));

    // The generated client is an interface. The REST client of Quarkus implements it.
    final var client = QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(URI.create(baseUrl))
        .connectTimeout(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)
        .readTimeout(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)
        .build(CreditRatingApi.class);

    return new CreditRatingRestAdapter(client, baseUrl);

  }

}
