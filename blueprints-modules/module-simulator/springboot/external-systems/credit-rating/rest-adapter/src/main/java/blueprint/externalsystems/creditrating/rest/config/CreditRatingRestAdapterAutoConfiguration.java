package blueprint.externalsystems.creditrating.rest.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.creditrating.rest.CreditRatingRestAdapter;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link CreditRating} bean and does nothing else.
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CreditRatingProperties.class)
public class CreditRatingRestAdapterAutoConfiguration {

  /**
   * The credit rating, over REST. Created while the application starts, so a missing address
   * stops the start with a message instead of failing the first loan request.
   *
   * @param restClients The REST client builder of Spring Boot, with its JSON converters.
   * @param properties  Where the credit rating is.
   * @return The adapter.
   */
  @Bean
  public CreditRating creditRating(
      final RestClient.Builder restClients,
      final CreditRatingProperties properties) {

    if ((properties.baseUrl() == null) || properties.baseUrl().isBlank()) {
      throw new IllegalStateException("""
          The credit rating has no address. Set 'credit-rating.base-url' to where it answers. \
          For the simulator of this blueprint that is http://localhost:8079/credit-rating, and \
          the profile 'local' of the application sets it.""");
    }

    final var timeouts = new SimpleClientHttpRequestFactory();
    timeouts.setConnectTimeout(properties.timeout());
    timeouts.setReadTimeout(properties.timeout());

    return new CreditRatingRestAdapter(
        restClients
            .baseUrl(properties.baseUrl())
            .requestFactory(timeouts)
            .build(), properties.baseUrl());

  }

}
