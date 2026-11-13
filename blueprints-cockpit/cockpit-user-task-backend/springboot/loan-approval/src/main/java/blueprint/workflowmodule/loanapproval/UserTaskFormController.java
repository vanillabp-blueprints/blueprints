package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;

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
 * The endpoints are written by hand, with the types as records below, rather than generated
 * from the OpenAPI. Four endpoints read better as code than as generator settings. The
 * integration test reads the OpenAPI and checks that every operation in it has its endpoint
 * here, so the two cannot drift apart unnoticed.
 * </p>
 *
 * <p>
 * Each form has two endpoints. GET reads what the form shows. POST takes what the user entered
 * and completes the user task. A task which is not open answers 404: the id is unknown, or the
 * task was completed already.
 * </p>
 */
@RestController
@RequestMapping("/loan-approval/api/user-tasks/{userTaskId}")
public class UserTaskFormController {

  @Autowired
  private Service loanApproval;

  /**
   * What the form "Assess risk" shows ({@code getAssessRiskForm}).
   *
   * @param userTaskId The id of the user task.
   * @return The loan request.
   */
  @GetMapping("/assess-risk")
  public AssessRiskForm getAssessRiskForm(
      @PathVariable final String userTaskId) {

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
  @PostMapping("/assess-risk")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeAssessRisk(
      @PathVariable final String userTaskId,
      @RequestBody final AssessRiskInput input) {

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
  @GetMapping("/check-documents")
  public CheckDocumentsForm getCheckDocumentsForm(
      @PathVariable final String userTaskId) {

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
  @PostMapping("/check-documents")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeCheckDocuments(
      @PathVariable final String userTaskId,
      @RequestBody final CheckDocumentsInput input) {

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

  /**
   * What the form "Assess risk" shows. A value which is not there is left out of the JSON, as
   * the OpenAPI describes it, rather than sent as null.
   *
   * @param loanRequestId  The id of the loan request.
   * @param amount         The amount requested.
   * @param riskAssessment Empty while the task is open, because nothing is stored before.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AssessRiskForm(
                               String loanRequestId,
                               Integer amount,
                               String riskAssessment) {
  }

  /**
   * What the user entered in the form "Assess risk".
   *
   * @param riskAssessment What the user concluded about the risk.
   */
  public record AssessRiskInput(
                                String riskAssessment) {
  }

  /**
   * What the form "Check documents" shows.
   *
   * @param loanRequestId  The id of the loan request.
   * @param riskAssessment What the user task "Assess risk" concluded.
   * @param documentsNote  Empty while the task is open, because nothing is stored before.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CheckDocumentsForm(
                                   String loanRequestId,
                                   String riskAssessment,
                                   String documentsNote) {
  }

  /**
   * What the user entered in the form "Check documents".
   *
   * @param documentsNote What the user noted about the documents.
   */
  public record CheckDocumentsInput(
                                    String documentsNote) {
  }

}
