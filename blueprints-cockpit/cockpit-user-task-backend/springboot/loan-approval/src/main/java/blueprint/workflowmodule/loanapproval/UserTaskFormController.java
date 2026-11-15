package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import blueprint.workflowmodule.loanapproval.api.UserTasksApi;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskForm;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.api.model.CheckDocumentsForm;
import blueprint.workflowmodule.loanapproval.api.model.CheckDocumentsInput;


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
 * The paths, the parameters and the records of the requests and answers come from
 * {@link UserTasksApi}, the interface the build generates from the same OpenAPI file. This class
 * only implements it. The OpenAPI names its paths below the server {@code /wm/loan-approval}, so
 * the interface maps {@code /api/user-tasks/...}. The {@code @RequestMapping} of this class puts
 * {@code /loan-approval} in front, which is where the request arrives.
 * </p>
 *
 * <p>
 * Each form has two endpoints. GET reads what the form shows. POST takes what the user entered
 * and completes the user task. A task which is not open answers 404: the id is unknown, or the
 * task was completed already.
 * </p>
 */
@RestController
@RequestMapping("/loan-approval")
public class UserTaskFormController implements UserTasksApi {

  @Autowired
  private Service loanApproval;

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
            loanRequest.getLoanRequestId(), loanRequest.getAmount(), loanRequest.getRiskAssessment()))
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

    final var riskAssessment = required(input.riskAssessment(), "riskAssessment");

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
            loanRequest.getLoanRequestId(), loanRequest.getRiskAssessment(), loanRequest.getDocumentsNote()))
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

    final var documentsNote = required(input.documentsNote(), "documentsNote");

    if (!loanApproval.checkDocuments(userTaskId, documentsNote)) {
      throw notOpen(userTaskId);
    }

  }

  private static String required(
      final String value,
      final String name) {

    if ((value == null) || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'"
          + name
          + "' is empty");
    }
    return value;

  }

  private static ResponseStatusException notOpen(
      final String userTaskId) {

    return new ResponseStatusException(
        HttpStatus.NOT_FOUND, "No open user task '"
            + userTaskId
            + "'");

  }

}
