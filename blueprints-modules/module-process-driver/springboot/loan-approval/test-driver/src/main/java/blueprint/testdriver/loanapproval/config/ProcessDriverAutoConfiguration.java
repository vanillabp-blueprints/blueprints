package blueprint.testdriver.loanapproval.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

import blueprint.testdriver.loanapproval.ProcessDriver;
import blueprint.testdriver.simulator.SimulatorTestApi;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires the process driver, but only if {@code process-driver.enabled} is {@code true}. Without
 * the property, this module adds no bean and no endpoint.
 *
 * <p>
 * The driver sits in a package of its own, not below the package of the workflow module. The
 * component scan of the module does not reach it, so the property decides alone.
 * </p>
 *
 * <p>
 * The driver plays the users through the client of the API of the user interface. The module
 * {@code loan-approval-gui-api-client} brings that client, at the address
 * {@code loan-approval-gui-api.client.base-url}.
 * </p>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnBooleanProperty("process-driver.enabled")
@ComponentScan(
    basePackageClasses = ProcessDriver.class,
    // The scan reaches this class as well. Registered twice, it would be applied twice.
    excludeFilters = @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class))
@EnableConfigurationProperties(ProcessDriverProperties.class)
public class ProcessDriverAutoConfiguration {

  /**
   * The client of the simulator's test API, at the address of the configuration.
   *
   * @param properties The configuration of the driver.
   * @return The client.
   */
  @Bean
  public SimulatorTestApi simulatorTestApi(
      final ProcessDriverProperties properties) {

    log.warn(
        "The process driver is on and uses the simulator at {}. It creates loan approvals on request."
            + " Never switch it on in production. Its states -> http://localhost:8080/loan-approval/api/process-driver/states",
        properties.getSimulatorBaseUrl());

    return SimulatorTestApi.at(properties.getSimulatorBaseUrl());

  }

}
