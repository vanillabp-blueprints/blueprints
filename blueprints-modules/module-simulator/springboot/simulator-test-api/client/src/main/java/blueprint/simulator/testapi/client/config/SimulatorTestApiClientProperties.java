package blueprint.simulator.testapi.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.vanillabp.cockpit.support.rest.adapter.Client;

/**
 * Where the simulator is and how the client of its test API talks to it, read from
 * {@code simulator-test-api.*}. The keys are the standard keys of a REST client of the Business
 * Cockpit support: {@code base-url}, {@code connect-timeout}, {@code read-timeout} and more. The
 * README of the support lists them all.
 */
@ConfigurationProperties(prefix = "simulator-test-api")
public class SimulatorTestApiClientProperties extends Client {

  /**
   * Creates the settings with the defaults of the support.
   */
  public SimulatorTestApiClientProperties() {
  }

}
