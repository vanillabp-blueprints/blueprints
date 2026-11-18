package blueprint.externalsystems.creditrating.rest.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.creditrating.api.client.CreditRatingApi;
import blueprint.externalsystems.creditrating.rest.CreditRatingRestAdapter;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link CreditRating} bean and does nothing else.
 *
 * <p>
 * The client comes from {@code credit-rating-api-client}, which builds it from the keys below
 * {@code credit-rating.client}. A missing address stops the start with a message which names the
 * key, instead of failing the first loan request.
 * </p>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
public class CreditRatingRestAdapterAutoConfiguration {

  /**
   * The credit rating, over REST.
   *
   * @param client The generated client of the credit rating.
   * @return The adapter.
   */
  @Bean
  public CreditRating creditRating(
      final CreditRatingApi client) {

    return new CreditRatingRestAdapter(client);

  }

}
