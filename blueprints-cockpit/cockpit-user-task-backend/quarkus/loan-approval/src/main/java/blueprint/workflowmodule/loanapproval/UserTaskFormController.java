package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.api.UserTasksApi;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskForm;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.api.model.CheckDocumentsForm;
import blueprint.workflowmodule.loanapproval.api.model.CheckDocumentsInput;
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
 * {@code /wm/loan-approval/api/user-tasks/...}, a path of the cockpit, which is the server the
 * OpenAPI names. The cockpit forwards the request to the {@code workflow-module-uri} of this
 * module and puts the path of that address in place of {@code /wm/loan-approval}. The address
 * ends with {@code /loan-approval}, so the request arrives here as
 * {@code /loan-approval/api/user-tasks/...}.
 * </p>
 *
 * <p>
 * The paths, the parameters and the classes of the requests and answers come from
 * {@link UserTasksApi}, the interface the build generates from the same OpenAPI file. This class
 * only implements it. The OpenAPI names its paths below the server {@code /wm/loan-approval}, so
 * the interface maps {@code /api/user-tasks/{userTaskId}}. The {@code @Path} of this class puts
 * {@code /loan-approval} in front, which is where the request arrives. Jakarta REST takes the
 * {@code @Path} of a class instead of the one of its interface, not in addition to it. So the
 * class repeats the path of the interface after {@code /loan-approval}. The methods keep the
 * paths, the parameters and the media types of the interface.
 * </p>
 *
 * <p>
 * Each form has two endpoints. GET reads what the form shows. POST takes what the user entered
 * and completes the user task. A task which is not open answers 404: the id is unknown, or the
 * task was completed already.
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
@Path("/loan-approval/api/user-tasks/{userTaskId}")
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
   * @param userTaskId The id of the user task.
   * @return The loan request.
   */
  @Override
  public AssessRiskForm getAssessRiskForm(
      final String userTaskId) {

    return loanApproval
        .riskAssessment(userTaskId)
        .map(loanRequest -> new AssessRiskForm(
            loanRequest.getLoanRequestId(), loanRequest.getAmount()).riskAssessment(loanRequest.getRiskAssessment()))
        .orElseThrow(() -> notOpen(userTaskId));

  }

  /**
   * Completes the user task "Assess risk" ({@code completeAssessRisk}).
   *
   * @param userTaskId The id of the user task.
   * @param input      What the user entered.
   */
  @Override
  public void completeAssessRisk(
      final String userTaskId,
      final AssessRiskInput input) {

    final var riskAssessment = required(input.getRiskAssessment(), "riskAssessment");
    refuseIfSomebodyElseTookOver(userTaskId);

    if (!loanApproval.assessRisk(userTaskId, riskAssessment)) {
      throw notOpen(userTaskId);
    }

  }

  /**
   * What the form "Check documents" shows ({@code getCheckDocumentsForm}).
   *
   * @param userTaskId The id of the user task.
   * @return The loan request, with the risk assessment of the first user task.
   */
  @Override
  public CheckDocumentsForm getCheckDocumentsForm(
      final String userTaskId) {

    return loanApproval
        .documentsCheck(userTaskId)
        .map(loanRequest -> new CheckDocumentsForm(
            loanRequest.getLoanRequestId(), loanRequest.getRiskAssessment())
            .documentsNote(loanRequest.getDocumentsNote()))
        .orElseThrow(() -> notOpen(userTaskId));

  }

  /**
   * Completes the user task "Check documents" ({@code completeCheckDocuments}).
   *
   * @param userTaskId The id of the user task.
   * @param input      What the user entered.
   */
  @Override
  public void completeCheckDocuments(
      final String userTaskId,
      final CheckDocumentsInput input) {

    final var documentsNote = required(input.getDocumentsNote(), "documentsNote");
    refuseIfSomebodyElseTookOver(userTaskId);

    if (!loanApproval.checkDocuments(userTaskId, documentsNote)) {
      throw notOpen(userTaskId);
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
      final String userTaskId) {

    if (cockpit.saysSomebodyElseTookOver(userTaskId, headers.getHeaderString(HttpHeaders.COOKIE))) {
      throw new WebApplicationException(
          "Somebody else took over the user task '"
              + userTaskId
              + "'", Status.CONFLICT);
    }

  }

  private static WebApplicationException notOpen(
      final String userTaskId) {

    return new WebApplicationException(
        "No open user task '"
            + userTaskId
            + "'", Status.NOT_FOUND);

  }

}
