package blueprint.externalsystems.creditrating.rest.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.creditrating.api.client.CreditRatingApi;
import blueprint.externalsystems.creditrating.api.client.config.CreditRatingClientAutoConfiguration;
import blueprint.externalsystems.creditrating.api.client.config.CreditRatingClientProperties;
import blueprint.externalsystems.creditrating.rest.CreditRatingRestAdapter;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link CreditRating} bean and does nothing else.
 *
 * <p>
 * The client comes from the client module of the API, which builds it out of the properties
 * {@code credit-rating.*}. This class only puts the adapter in front of it. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration(after = CreditRatingClientAutoConfiguration.class)
public class CreditRatingRestAdapterAutoConfiguration {

  /**
   * Creates the configuration. Spring creates it, nobody else does.
   */
  public CreditRatingRestAdapterAutoConfiguration() {
  }

  /**
   * The credit rating, over REST.
   *
   * @param client     The generated client, built by the client module.
   * @param properties Where the credit rating is, for the messages of the adapter.
   * @return The adapter.
   */
  @Bean
  public CreditRating creditRating(
      final CreditRatingApi client,
      final CreditRatingClientProperties properties) {

    return new CreditRatingRestAdapter(client, properties.getBaseUrl());

  }

}
