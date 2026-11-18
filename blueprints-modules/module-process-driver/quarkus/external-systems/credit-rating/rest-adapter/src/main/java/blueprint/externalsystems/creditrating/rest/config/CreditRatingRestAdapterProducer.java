package blueprint.externalsystems.creditrating.rest.config;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.creditrating.api.client.CreditRatingApi;
import blueprint.externalsystems.creditrating.rest.CreditRatingRestAdapter;
import io.quarkus.runtime.Startup;
import io.vanillabp.cockpit.support.rest.restclient.RestClients;
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

  /** Where the keys of the client are, for example {@code credit-rating.client.base-url}. */
  public static final String KEYS = "credit-rating.client";

  /**
   * The credit rating, over REST. The support library of the Business Cockpit builds the
   * generated client from the keys below {@value #KEYS}: {@code base-url}, the timeouts, a
   * proxy, retries and the rest. It is created while the application starts, so a missing
   * address stops the start with a message which names the key, instead of failing the first
   * loan request.
   *
   * @param restClients The builder of the support library.
   * @return The adapter.
   */
  @Produces
  @Singleton
  @Startup
  public CreditRating creditRating(
      final RestClients restClients) {

    return new CreditRatingRestAdapter(restClients.build(CreditRatingApi.class, KEYS));

  }

}
