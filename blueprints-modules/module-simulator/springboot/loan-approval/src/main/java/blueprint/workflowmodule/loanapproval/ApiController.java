package blueprint.workflowmodule.loanapproval;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import blueprint.externalsystems.creditrating.CreditRatingUnavailableException;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. It consists of GET requests only, so the process can be walked
 * through in a browser. No tooling, no request bodies.
 *
 * <p>
 * It talks to {@link Service} and to nothing else. When a system around the module does not
 * answer while a user waits, the user gets a 503 which names the role of that system.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/loan-approval")
public class ApiController {

  /**
   * The answer to a loan request.
   *
   * @param loanRequestId The id of the loan request.
   * @param outcome       {@code started} or {@code declined}.
   */
  public record Answer(
                       String loanRequestId,
                       String outcome) {
  }

  /**
   * The answer when a system around the module does not answer, in the fields of a problem
   * detail (RFC 9457). It is a record of its own and not Spring's {@code ProblemDetail}, because
   * how that class is written as JSON depends on the Jackson version on the classpath, and an
   * engine may bring another one.
   *
   * @param status         The HTTP status, 503.
   * @param title          A short summary.
   * @param detail         What the user can do.
   * @param externalSystem The role of the system which does not answer.
   */
  public record Unavailable(
                            int status,
                            String title,
                            String detail,
                            String externalSystem) {
  }

  @Autowired
  private Service loanApproval;

  /**
   * Requests a loan. This is the URL the README names.
   *
   * @param customerId The customer asking for the loan.
   * @param amount     The amount requested.
   * @return Whether a workflow started for the loan request.
   */
  @GetMapping("/request")
  public Answer request(
      @RequestParam(defaultValue = "C-1001") final String customerId,
      @RequestParam(defaultValue = "5000") final int amount) {

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
  @GetMapping("/{loanRequestId}/prepare-contract/{taskId}")
  public String prepareContract(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      @RequestParam final BigDecimal interestRate) {

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
  @GetMapping("/{loanRequestId}")
  public String show(
      @PathVariable final String loanRequestId) {

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
