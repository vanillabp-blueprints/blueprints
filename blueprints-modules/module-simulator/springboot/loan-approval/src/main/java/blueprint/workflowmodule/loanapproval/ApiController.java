package blueprint.workflowmodule.loanapproval;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import blueprint.externalsystems.creditrating.CreditRatingUnavailableException;
import blueprint.workflowmodule.loanapproval.api.LoanApprovalApi;
import blueprint.workflowmodule.loanapproval.api.model.Answer;
import blueprint.workflowmodule.loanapproval.api.model.Unavailable;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. It consists of GET requests only, so the process can be walked
 * through in a browser. No tooling, no request bodies.
 *
 * <p>
 * It talks to {@link Service} and to nothing else. When a system around the module does not
 * answer while a user waits, the user gets a 503 which names the role of that system.
 * </p>
 *
 * <p>
 * The paths, the parameters and the records {@link Answer} and {@link Unavailable} come from
 * {@link LoanApprovalApi}, the interface the build generates from
 * {@code src/main/resources/openapi/loan-approval.yaml}. This class only implements it.
 * </p>
 */
@Slf4j
@RestController
public class ApiController implements LoanApprovalApi {

  @Autowired
  private Service loanApproval;

  /**
   * Requests a loan. This is the URL the README names.
   *
   * @param customerId The customer asking for the loan.
   * @param amount     The amount requested.
   * @return Whether a workflow started for the loan request.
   */
  @Override
  public Answer request(
      final String customerId,
      final Integer amount) {

    final var loanRequestId = UUID
        .randomUUID()
        .toString();

    if (!loanApproval.request(loanRequestId, customerId, amount)) {
      return new Answer(loanRequestId, "declined");
    }

    log.info(
        "Show the loan approval -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return new Answer(loanRequestId, "started");

  }

  /**
   * Completes the user task "Prepare contract".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task, as the log and the cockpit show it.
   * @param interestRate  The interest rate of the contract, in percent.
   * @return A confirmation.
   */
  @Override
  public String prepareContract(
      final String loanRequestId,
      final String taskId,
      final BigDecimal interestRate) {

    loanApproval.prepareContract(loanRequestId, taskId, interestRate);

    return "Contract of loan approval '"
        + loanRequestId
        + "' prepared";

  }

  /**
   * Shows what the process did.
   *
   * @param loanRequestId The id returned by the loan request.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String loanRequestId) {

    return loanApproval
        .get(loanRequestId)
        .map(Object::toString)
        .orElse("unknown loan request '"
            + loanRequestId
            + "'");

  }

  /**
   * The credit rating does not answer while the user waits for it. The answer says which system
   * is missing, by its role, so the user knows it is not the application which failed. The
   * product behind the role stays inside the adapter.
   *
   * @param e What the adapter reported.
   * @return A problem detail with status 503.
   */
  @ExceptionHandler(CreditRatingUnavailableException.class)
  public ResponseEntity<Unavailable> creditRatingUnavailable(
      final CreditRatingUnavailableException e) {

    log.warn("{}", e.getMessage());

    return ResponseEntity
        .status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new Unavailable(
            HttpStatus.SERVICE_UNAVAILABLE
                .value(), "Service Unavailable", "The credit rating does not answer. Try again later.", CreditRatingUnavailableException.ROLE));

  }

}
