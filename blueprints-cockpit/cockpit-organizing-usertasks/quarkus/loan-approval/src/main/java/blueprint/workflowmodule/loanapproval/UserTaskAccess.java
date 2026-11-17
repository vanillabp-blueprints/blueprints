package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.config.Permission;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.security.PermissionChecker;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Who may read the form of a user task without the authority for it: everybody who worked on
 * the task before. The endpoints which read a form name a permission of this bean in their
 * {@code @PermissionsAllowed}. Its checker lets in the authority of the task, and the earlier
 * editors.
 *
 * <p>
 * The Business Cockpit shows the task to the same people, because the details provider reports
 * them as the admitted users of the task. Both read the same list, the editors of the task.
 * That is the point: if the cockpit showed a task and the form then answered 403, the admitted
 * users of the cockpit would be worth nothing.
 * </p>
 *
 * <p>
 * It is an ordinary bean, and it reads only the ids of the editors, not the whole case. The
 * check runs on every request to a form.
 * </p>
 */
@ApplicationScoped
public class UserTaskAccess {

  /** Reads the form "Call customer". */
  public static final String READ_CALL_CUSTOMER = "loan-approval-read-call-customer";

  /** Reads the form "Assess risk". */
  public static final String READ_ASSESS_RISK = "loan-approval-read-assess-risk";

  /** Reads the form "Approve loan". */
  public static final String READ_APPROVE_LOAN = "loan-approval-read-approve-loan";

  @Inject
  AggregateRepository loanApprovals;

  /**
   * Who may read the form "Call customer".
   *
   * @param user          The user who asks.
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return True if the user holds the authority of the task or worked on it before.
   */
  @PermissionChecker(READ_CALL_CUSTOMER)
  @Blocking
  boolean mayReadTheCallCustomerForm(
      final SecurityIdentity user,
      final String loanRequestId,
      final String taskId) {

    return user.hasRole(Permission.CALL_CUSTOMER) || mayRead(loanRequestId, taskId, user.getPrincipal().getName());

  }

  /**
   * Who may read the form "Assess risk".
   *
   * @param user          The user who asks.
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return True if the user holds the authority of the task or worked on it before.
   */
  @PermissionChecker(READ_ASSESS_RISK)
  @Blocking
  boolean mayReadTheAssessRiskForm(
      final SecurityIdentity user,
      final String loanRequestId,
      final String taskId) {

    return user.hasRole(Permission.ASSESS_RISK) || mayRead(loanRequestId, taskId, user.getPrincipal().getName());

  }

  /**
   * Who may read the form "Approve loan".
   *
   * @param user          The user who asks.
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return True if the user holds the authority of the task or worked on it before.
   */
  @PermissionChecker(READ_APPROVE_LOAN)
  @Blocking
  boolean mayReadTheApproveLoanForm(
      final SecurityIdentity user,
      final String loanRequestId,
      final String taskId) {

    return user.hasRole(Permission.APPROVE_LOAN) || mayRead(loanRequestId, taskId, user.getPrincipal().getName());

  }

  /**
   * Whether the user worked on the user task before and may read it.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param user          The user who asks.
   * @return True if the user opened the form to work on it, or completed it.
   */
  @Transactional
  public boolean mayRead(
      final String loanRequestId,
      final String taskId,
      final String user) {

    return loanApprovals
        .findEditors(loanRequestId, taskId)
        .contains(user);

  }

}
