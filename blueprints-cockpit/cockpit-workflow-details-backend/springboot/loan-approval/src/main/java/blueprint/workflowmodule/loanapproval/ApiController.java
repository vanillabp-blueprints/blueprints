package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * The API for a human with a browser: it starts a loan approval, approves it and shows it. It
 * consists of GET requests only, so it works in a browser. The case page has an API of its own,
 * in {@link CasePageController}.
 *
 * <p>
 * Every path of this module starts with {@code /loan-approval}, the workflow module ID. The
 * Business Cockpit forwards requests for this module to that path, and an application which
 * holds several workflow modules keeps their paths apart this way.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/loan-approval/api/loan-requests")
public class ApiController {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL to remember. The case then shows up in the
   * case list of the Business Cockpit.
   *
   * @param applicant Who asks for the loan.
   * @param amount    The amount requested.
   * @return The id of the loan request started.
   */
  @GetMapping("/start")
  public String start(
      @RequestParam(defaultValue = "Jane Doe") final String applicant,
      @RequestParam(defaultValue = "5000") final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, applicant, amount);

    log.info(
        "Show the result -> http://localhost:8080/loan-approval/api/loan-requests/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * Approves the loan request, which completes the user task. The case ends the regular way.
   *
   * <p>
   * In a real application, the form of the user task does this. Forms are the subject of the
   * blueprint {@code cockpit-user-task}, so this blueprint has none. This URL stands in for it,
   * so you can see an approved loan request next to a withdrawn one.
   * </p>
   *
   * @param loanRequestId The id returned by starting the process.
   * @return What happened.
   */
  @GetMapping("/{loanRequestId}/approve")
  public String approve(
      @PathVariable final String loanRequestId) {

    return switch (loanApproval.approve(loanRequestId)) {
      case DONE -> "approved";
      case NOT_FOUND -> "unknown loan request '"
          + loanRequestId
          + "'";
      case NOT_POSSIBLE -> "the loan request '"
          + loanRequestId
          + "' is not waiting for a review";
    };

  }

  /**
   * Shows what the process did.
   *
   * @param loanRequestId The id returned by starting the process.
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

}
