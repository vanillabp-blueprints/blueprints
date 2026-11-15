package blueprint.externalsystems.esignature.api.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.vanillabp.cockpit.support.rest.adapter.Client;

/**
 * Where the e-signature service is and how the client talks to it, read from
 * {@code e-signature.*}. The keys are the standard keys of a REST client of the Business Cockpit
 * support: {@code base-url}, {@code connect-timeout}, {@code read-timeout}, {@code retry.*},
 * {@code authentication.*} and more. The README of the support lists them all.
 *
 * <p>
 * The application sets the values per profile. There is no default address, because no address
 * is right for every environment.
 * </p>
 */
@ConfigurationProperties(prefix = "e-signature")
public class ESignatureClientProperties extends Client {

  /**
   * Creates the settings. Connecting and answering may take five seconds each.
   */
  public ESignatureClientProperties() {

    setConnectTimeout(5000);
    setReadTimeout(5000);

  }

}
