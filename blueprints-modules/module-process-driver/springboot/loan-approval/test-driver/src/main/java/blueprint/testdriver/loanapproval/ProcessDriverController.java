package blueprint.testdriver.loanapproval;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import blueprint.testdriver.loanapproval.api.ProcessDriverApi;
import blueprint.testdriver.loanapproval.api.model.Reached;
import blueprint.testdriver.loanapproval.steps.ProcessStateNotReachedException;

/**
 * The process driver over HTTP, for the dev shell and for the tests of a user interface. They
 * bring a loan approval to a state and then show or click the open user task.
 *
 * <p>
 * It exists only where this module is on the classpath and {@code process-driver.enabled} is
 * {@code true}. The application in production has neither. The paths come from
 * {@link ProcessDriverApi}, which the build generates from
 * {@code src/main/resources/openapi/process-driver.yaml}.
 * </p>
 */
@RestController
public class ProcessDriverController implements ProcessDriverApi {

  @Autowired
  private ProcessDriver driver;

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

    return new Reached(
        scenario.getState().name(), scenario.getLoanRequestId(), scenario.getCustomerId(), driver
            .openUserTaskId(scenario));

  }

  /**
   * The name of the state is unknown.
   *
   * @param e What {@link ProcessState#valueOf(String)} reported.
   * @return The reason, with status 400.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> unknownState(
      final IllegalArgumentException e) {

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.TEXT_PLAIN)
        .body(e.getMessage()
            + ". Known states: "
            + states());

  }

  /**
   * The loan request did not reach the state.
   *
   * @param e The exception of the driver, whose message says why.
   * @return The reason, with status 500.
   */
  @ExceptionHandler(ProcessStateNotReachedException.class)
  public ResponseEntity<String> notReached(
      final ProcessStateNotReachedException e) {

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.TEXT_PLAIN)
        .body(e.getMessage());

  }

}
