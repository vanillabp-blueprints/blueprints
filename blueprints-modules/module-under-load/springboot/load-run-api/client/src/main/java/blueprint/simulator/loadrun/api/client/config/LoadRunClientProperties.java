package blueprint.simulator.loadrun.api.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.vanillabp.cockpit.support.rest.adapter.Client;

/**
 * Where the simulator which drives the load run is and how the client talks to it, read from {@code
 * load-run-api.*}. The keys are the standard keys of a REST client of the Business Cockpit support:
 * {@code base-url}, {@code connect-timeout}, {@code read-timeout}, {@code retry.*}, {@code
 * authentication.*} and more. The README of the support lists them all.
 */
@ConfigurationProperties(prefix = "load-run-api")
public class LoadRunClientProperties extends Client {

  /**
   * Creates the settings with the defaults of the support.
   */
  public LoadRunClientProperties() {
  }

}
