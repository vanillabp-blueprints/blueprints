package blueprint.externalsystems.creditrating.rest.config;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.creditrating.api.client.CreditRatingApi;
import blueprint.externalsystems.creditrating.api.client.config.CreditRatingClientProperties;
import blueprint.externalsystems.creditrating.rest.CreditRatingRestAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link CreditRating} bean and does nothing else.
 *
 * <p>
 * The client comes from the client module of the API, which builds it out of the properties
 * {@code credit-rating.*}. This class only puts the adapter in front of it. Quarkus finds this
 * class through the index the build of this module writes.
 * </p>
 */
@ApplicationScoped
public class CreditRatingRestAdapterProducer {

  /**
   * The credit rating, over REST.
   *
   * @param client     The generated client, built by the client module.
   * @param properties Where the credit rating is, for the messages of the adapter.
   * @return The adapter.
   */
  @Produces
  @Singleton
  public CreditRating creditRating(
      final CreditRatingApi client,
      final CreditRatingClientProperties properties) {

    return new CreditRatingRestAdapter(client, properties.baseUrl().orElseThrow());

  }

}
