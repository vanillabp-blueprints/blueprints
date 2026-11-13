package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.config.Permission;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case for people: GET requests only, so a loan approval can be started,
 * looked at and handed to another advisor in a browser. The forms of the user tasks have
 * endpoints of their own, in {@link UserTaskFormController}.
 *
 * <p>
 * The user who logged in is passed on to {@link Service}, which writes it into the aggregate.
 * That is how a report to the Business Cockpit can later name who caused it.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/loan-approval")
public class ApiController {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL to remember. The URLs of the forms are logged
   * once the user tasks are open.
   *
   * @param amount   The amount requested.
   * @param customer The name of the customer.
   * @param phone    The phone number of the customer.
   * @param user     The advisor who logged in.
   * @return The id of the loan request started.
   */
  @GetMapping("/start")
  @Secured(Permission.REQUEST_LOAN)
  public String start(
      @RequestParam(defaultValue = "5000") final int amount,
      @RequestParam(defaultValue = "Jane Roe") final String customer,
      @RequestParam(defaultValue = "+43 1 2345 678") final String phone,
      final Authentication user) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, amount, customer, phone, user.getName());

    log.info(
        "Show the result -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * Hands the customer of a loan approval to another advisor. If the old advisor still has to
   * call the customer, that task moves to the new one.
   *
   * @param loanRequestId The id of the loan request.
   * @param advisor       The user id of the new advisor.
   * @param user          Who makes the change.
   * @return What happened.
   */
  @GetMapping("/{loanRequestId}/change-advisor")
  @Secured(Permission.CHANGE_ADVISOR)
  public String changeAdvisor(
      @PathVariable final String loanRequestId,
      @RequestParam final String advisor,
      final Authentication user) {

    loanApproval.changeAdvisor(loanRequestId, advisor, user.getName());

    return "The customer of loan approval '"
        + loanRequestId
        + "' has the advisor '"
        + advisor
        + "' now";

  }

  /**
   * Shows what the process did: the aggregate, with every user task and its data.
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

  /**
   * A change which does not fit the loan approval as it is now.
   *
   * @param e What the service found.
   * @return The message.
   */
  @ExceptionHandler
  @ResponseStatus(HttpStatus.CONFLICT)
  public String notNow(
      final IllegalStateException e) {

    return e.getMessage();

  }

}
