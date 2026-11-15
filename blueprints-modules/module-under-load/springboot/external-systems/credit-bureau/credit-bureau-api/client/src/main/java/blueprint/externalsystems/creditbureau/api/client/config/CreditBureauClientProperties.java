package blueprint.externalsystems.creditbureau.api.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.vanillabp.cockpit.support.rest.adapter.Client;

/**
 * Where the credit bureau is and how the client talks to it, read from {@code credit-bureau.*}. The
 * keys are the standard keys of a REST client of the Business Cockpit support: {@code base-url},
 * {@code connect-timeout}, {@code read-timeout}, {@code retry.*}, {@code authentication.*} and
 * more. The README of the support lists them all.
 */
@ConfigurationProperties(prefix = "credit-bureau")
public class CreditBureauClientProperties extends Client {

  /**
   * Creates the settings. Connecting and answering may take five seconds each.
   */
  public CreditBureauClientProperties() {

    setConnectTimeout(5000);
    setReadTimeout(5000);

  }

}
