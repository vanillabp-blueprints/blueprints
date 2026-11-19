package blueprint.testdriver.loanapproval;

import java.util.Arrays;
import java.util.List;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import blueprint.testdriver.loanapproval.api.ProcessDriverApi;
import blueprint.testdriver.loanapproval.api.model.Reached;
import blueprint.testdriver.loanapproval.steps.ProcessStateNotReachedException;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

/**
 * The process driver over HTTP, for the dev shell and for the tests of a user interface. They
 * bring a loan approval to a state and then show or click the open user task.
 *
 * <p>
 * It exists only where this module is on the classpath and {@code process-driver.enabled} is
 * {@code true}. The application in production has neither. The property is a build property, so
 * Quarkus leaves this resource out of an application which is built without it. The paths come
 * from
 * {@link ProcessDriverApi}, which the build generates from
 * {@code src/main/resources/loan-approval/openapi/process-driver.yaml}.
 * </p>
 */
@ApplicationScoped
@IfBuildProperty(name = "process-driver.enabled", stringValue = "true")
public class ProcessDriverController implements ProcessDriverApi {

  @Inject
  ProcessDriver driver;

  /**
   * The states a loan approval can be brought to.
   *
   * @return The names of the states.
   */
  @Override
  public List<String> states() {

    return Arrays
        .stream(ProcessState.values())
        .map(ProcessState::name)
        .toList();

  }

  /**
   * Brings a new loan request to a state.
   *
   * @param state       The name of the state.
   * @param amount      The amount, or null for the default of {@link TestData}.
   * @param creditScore The credit score, or null for the default of {@link TestData}.
   * @return The loan request, once it reached the state.
   */
  @Override
  public Reached advanceTo(
      final String state,
      final Integer amount,
      final Integer creditScore) {

    var testData = TestData.defaults();
    if (amount != null) {
      testData = testData.withAmount(amount);
    }
    if (creditScore != null) {
      testData = testData.withCreditScore(creditScore);
    }

    final var scenario = driver.advanceTo(ProcessState.valueOf(state), testData);

    // The generator of this platform writes a constructor of the required fields only. The open
    // user task is optional, because a state where the workflow ended has none.
    return new Reached(
        scenario.getState().name(), scenario.getLoanRequestId(), scenario.getCustomerId())
        .userTaskId(driver.openUserTaskId(scenario));

  }

  /**
   * The name of the state is unknown.
   *
   * @param e What {@link ProcessState#valueOf(String)} reported.
   * @return The reason, with status 400.
   */
  @ServerExceptionMapper(IllegalArgumentException.class)
  public RestResponse<String> unknownState(
      final IllegalArgumentException e) {

    return RestResponse.ResponseBuilder
        .create(RestResponse.Status.BAD_REQUEST, e.getMessage()
            + ". Known states: "
            + states())
        .type(MediaType.TEXT_PLAIN_TYPE)
        .build();

  }

  /**
   * The loan request did not reach the state.
   *
   * @param e The exception of the driver, whose message says why.
   * @return The reason, with status 500.
   */
  @ServerExceptionMapper(ProcessStateNotReachedException.class)
  public RestResponse<String> notReached(
      final ProcessStateNotReachedException e) {

    return RestResponse.ResponseBuilder
        .create(RestResponse.Status.INTERNAL_SERVER_ERROR, e.getMessage())
        .type(MediaType.TEXT_PLAIN_TYPE)
        .build();

  }

}
