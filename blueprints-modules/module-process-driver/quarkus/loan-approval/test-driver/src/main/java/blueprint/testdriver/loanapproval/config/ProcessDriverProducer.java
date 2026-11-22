package blueprint.testdriver.loanapproval.config;

import blueprint.testdriver.simulator.SimulatorTestApi;
import blueprint.workflowmodule.loanapproval.api.client.LoanApprovalApi;
import io.quarkus.arc.properties.IfBuildProperty;
import io.vanillabp.cockpit.support.rest.restclient.RestClients;
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
   * Where the keys of the client of the API of the user interface are, for example
   * {@code loan-approval-gui-api.client.base-url}.
   */
  public static final String GUI_API_KEYS = "loan-approval-gui-api.client";

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
   * The client of the API of the user interface of the workflow module, from the module
   * {@code loan-approval-gui-api-client}. The driver uses it like the browser of a person does. The
   * support library of the Business Cockpit builds it from the keys below {@value #GUI_API_KEYS}.
   *
   * @param restClients The builder of the support library.
   * @return The client.
   */
  @Produces
  @Singleton
  public LoanApprovalApi loanApprovalApi(
      final RestClients restClients) {

    return restClients.build(LoanApprovalApi.class, GUI_API_KEYS);

  }

}
