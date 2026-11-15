package blueprint.externalsystems.creditrating.api.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.vanillabp.cockpit.support.rest.adapter.Client;

/**
 * Where the credit rating is and how the client talks to it, read from {@code credit-rating.*}.
 * The keys are the standard keys of a REST client of the Business Cockpit support:
 * {@code base-url}, {@code connect-timeout}, {@code read-timeout}, {@code retry.*},
 * {@code authentication.*} and more. The README of the support lists them all.
 *
 * <p>
 * The application sets the values: the address of the simulator in the profile {@code local},
 * the address of the real system in the profile {@code real}. There is no default address,
 * because no address is right for every environment.
 * </p>
 */
@ConfigurationProperties(prefix = "credit-rating")
public class CreditRatingClientProperties extends Client {

  /**
   * Creates the settings. The user waits for the answer of the credit rating, so connecting and
   * answering may take two seconds each, instead of the longer defaults of the support.
   */
  public CreditRatingClientProperties() {

    setConnectTimeout(2000);
    setReadTimeout(2000);

  }

}
