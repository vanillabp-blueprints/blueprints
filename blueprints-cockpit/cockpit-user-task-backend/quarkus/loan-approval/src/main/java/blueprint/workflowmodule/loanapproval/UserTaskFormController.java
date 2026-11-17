package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.api.UserTasksApi;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskForm;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.api.model.CheckDocumentsForm;
import blueprint.workflowmodule.loanapproval.api.model.CheckDocumentsInput;
import io.vanillabp.cockpit.support.usertask.BcTaskClaimedByAnotherUserException;
import io.vanillabp.cockpit.support.usertask.BcTaskNotOpenException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response.Status;

/**
 * The endpoints the forms of the two user tasks call. They implement
 * {@code src/main/resources/openapi/loan-approval.yaml} of this module, which the frontends read
 * to generate their client.
 *
 * <p>
 * A form runs in the browser, inside the Business Cockpit. It calls
 * {@code /wm/loan-approval/api/loan-approval/...}, a path of the cockpit, which is the server the
 * OpenAPI names. The cockpit forwards the request to the {@code workflow-module-uri} of this
 * module and puts the path of that address in place of {@code /wm/loan-approval}. The address
 * ends with {@code /loan-approval}, so the request arrives here as
 * {@code /loan-approval/api/loan-approval/...}.
 * </p>
 *
 * <p>
 * The paths, the parameters and the classes of the requests and answers come from
 * {@link UserTasksApi}, the interface the build generates from the same OpenAPI file. This class
 * only implements it. The OpenAPI names its paths below the server {@code /wm/loan-approval}, so
 * the interface maps {@code /api/loan-approval/{loanRequestId}}. The {@code @Path} of this class puts
 * {@code /loan-approval} in front, which is where the request arrives. Jakarta REST takes the
 * {@code @Path} of a class instead of the one of its interface, not in addition to it. So the
 * class repeats the path of the interface after {@code /loan-approval}. The methods keep the
 * paths, the parameters and the media types of the interface.
 * </p>
 *
 * <p>
 * Each form has two endpoints. GET reads what the form shows. POST takes what the user entered
 * and completes the user task. Both name the loan request and the id of the user task. A task
 * which is not open on that loan request answers 404: the id is unknown, the task belongs to
 * another loan request, or it was completed already. Checking the loan request as well keeps a
 * form from completing the task of another case by mistake.
 * </p>
 *
 * <p>
 * The exceptions of both answers come from the support library of the Business Cockpit. Its
 * mappers answer them. Quarkus finds the mappers in the index of that library, so the controller
 * needs nothing for it.
 * </p>
 *
 * <p>
 * POST answers 409 if somebody else took the task over in the cockpit. Two users may open the
 * same task, and the cockpit lets one of them take it over. The other one still has the form on
 * the screen. Completing it would take the work out of the hands of the user who has it now. So
 * the module asks the {@link Cockpit} first, and refuses. Reading the form stays allowed,
 * because reading takes nothing away from anybody.
 * </p>
 */
@ApplicationScoped
@Path("/loan-approval/api/loan-approval/{loanRequestId}")
public class UserTaskFormController implements UserTasksApi {

  @Inject
  Service loanApproval;

  @Inject
  Cockpit cockpit;

  /** The headers of the request being answered. Quarkus injects a proxy which reaches the current ones. */
  @Inject
  HttpHeaders headers;

  /**
   * What the form "Assess risk" shows ({@code getAssessRiskForm}).
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The loan request.
   */
  @Override
  public AssessRiskForm getAssessRiskForm(
      final String loanRequestId,
      final String taskId) {

    return loanApproval
        .riskAssessment(loanRequestId, taskId)
        .map(loanRequest -> new AssessRiskForm(
            loanRequest.getLoanRequestId(), loanRequest.getAmount()).riskAssessment(loanRequest.getRiskAssessment()))
        .orElseThrow(() -> notOpen(loanRequestId, taskId));

  }

  /**
   * Completes the user task "Assess risk" ({@code completeAssessRisk}).
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user entered.
   */
  @Override
  public void completeAssessRisk(
      final String loanRequestId,
      final String taskId,
      final AssessRiskInput input) {

    final var riskAssessment = required(input.getRiskAssessment(), "riskAssessment");
    refuseIfSomebodyElseTookOver(loanRequestId, taskId);

    if (!loanApproval.assessRisk(loanRequestId, taskId, riskAssessment)) {
      throw notOpen(loanRequestId, taskId);
    }

  }

  /**
   * What the form "Check documents" shows ({@code getCheckDocumentsForm}).
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The loan request, with the risk assessment of the first user task.
   */
  @Override
  public CheckDocumentsForm getCheckDocumentsForm(
      final String loanRequestId,
      final String taskId) {

    return loanApproval
        .documentsCheck(loanRequestId, taskId)
        .map(loanRequest -> new CheckDocumentsForm(
            loanRequest.getLoanRequestId(), loanRequest.getRiskAssessment())
            .documentsNote(loanRequest.getDocumentsNote()))
        .orElseThrow(() -> notOpen(loanRequestId, taskId));

  }

  /**
   * Completes the user task "Check documents" ({@code completeCheckDocuments}).
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param input         What the user entered.
   */
  @Override
  public void completeCheckDocuments(
      final String loanRequestId,
      final String taskId,
      final CheckDocumentsInput input) {

    final var documentsNote = required(input.getDocumentsNote(), "documentsNote");
    refuseIfSomebodyElseTookOver(loanRequestId, taskId);

    if (!loanApproval.checkDocuments(loanRequestId, taskId, documentsNote)) {
      throw notOpen(loanRequestId, taskId);
    }

  }

  private static String required(
      final String value,
      final String name) {

    if ((value == null) || value.isBlank()) {
      throw new WebApplicationException("'"
          + name
          + "' is empty", Status.BAD_REQUEST);
    }
    return value;

  }

  private void refuseIfSomebodyElseTookOver(
      final String loanRequestId,
      final String taskId) {

    if (cockpit.saysSomebodyElseTookOver(taskId, headers.getHeaderString(HttpHeaders.COOKIE))) {
      throw new BcTaskClaimedByAnotherUserException(loanRequestId, taskId);
    }

  }

  private static BcTaskNotOpenException notOpen(
      final String loanRequestId,
      final String taskId) {

    return new BcTaskNotOpenException(loanRequestId, taskId);

  }

}
