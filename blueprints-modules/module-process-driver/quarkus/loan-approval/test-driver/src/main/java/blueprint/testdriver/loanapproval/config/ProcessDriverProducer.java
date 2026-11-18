package blueprint.testdriver.loanapproval.config;

import blueprint.testdriver.GeneratedClients;
import blueprint.testdriver.loanapproval.gui.LoanApprovalApi;
import blueprint.testdriver.simulator.SimulatorTestApi;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Makes the two clients of the process driver, but only if {@code process-driver.enabled} is
 * {@code true}. Without the property, this module adds no bean and no endpoint.
 *
 * <p>
 * The property is a build property. Every bean of the driver says {@code @IfBuildProperty} for it,
 * so Quarkus leaves them all out of an application which is built without it.
 * </p>
 */
@Slf4j
@Singleton
@IfBuildProperty(name = "process-driver.enabled", stringValue = "true")
public class ProcessDriverProducer {

  /**
   * The client of the simulator's test API, at the address of the configuration.
   *
   * @param properties The configuration of the driver.
   * @return The client.
   */
  @Produces
  @Singleton
  public SimulatorTestApi simulatorTestApi(
      final ProcessDriverProperties properties) {

    log.warn(
        "The process driver is on and uses the simulator at {}. It creates loan approvals on request."
            + " Never switch it on in production. Its states -> http://localhost:8080/loan-approval/api/process-driver/states",
        properties.simulatorBaseUrl());

    return SimulatorTestApi.at(properties.simulatorBaseUrl());

  }

  /**
   * The client of the API of the user interface of the workflow module, generated from its
   * OpenAPI file. The driver uses it like the browser of a person does.
   *
   * @param properties The configuration of the driver.
   * @return The client.
   */
  @Produces
  @Singleton
  public LoanApprovalApi loanApprovalApi(
      final ProcessDriverProperties properties) {

    return GeneratedClients
        .at(properties.applicationBaseUrl())
        .of(LoanApprovalApi.class);

  }

}
