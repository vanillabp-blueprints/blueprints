package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.api.LoanApprovalApi;
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
 *
 * <p>
 * The paths, the parameters and the answers come from {@link LoanApprovalApi}, the interface
 * the build generates from {@code src/main/resources/openapi/loan-approval.yaml}. This class
 * only implements it, and adds the permission of each endpoint.
 * </p>
 */
@Slf4j
@RestController
public class ApiController implements LoanApprovalApi {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL to remember. The URLs of the forms are logged
   * once the user tasks are open.
   *
   * @param amount   The amount requested.
   * @param customer The name of the customer.
   * @param phone    The phone number of the customer.
   * @return The id of the loan request started. The user logged in is the advisor.
   */
  @Override
  @Secured(Permission.REQUEST_LOAN)
  public String start(
      final Integer amount,
      final String customer,
      final String phone) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, amount, customer, phone, whoIsLoggedIn());

    log.info(
        "Show the result -> http://localhost:8080/loan-approval/api/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * Hands the customer of a loan approval to another advisor. If the old advisor still has to
   * call the customer, that task moves to the new one.
   *
   * @param loanRequestId The id of the loan request.
   * @param advisor       The user id of the new advisor.
   * @return What happened. The user logged in makes the change.
   */
  @Override
  @Secured(Permission.CHANGE_ADVISOR)
  public String changeAdvisor(
      final String loanRequestId,
      final String advisor) {

    loanApproval.changeAdvisor(loanRequestId, advisor, whoIsLoggedIn());

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
  @Override
  public String show(
      final String loanRequestId) {

    return loanApproval
        .describe(loanRequestId)
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

  /**
   * The user Spring Security logged in for this request. The generated interface knows nothing
   * about security, so the name is taken from the security context and never from the request.
   *
   * @return The name of the user.
   */
  static String whoIsLoggedIn() {

    return SecurityContextHolder
        .getContext()
        .getAuthentication()
        .getName();

  }

}
