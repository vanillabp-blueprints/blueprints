package blueprint.workflowmodule.loanapproval;

import java.util.List;
import java.util.UUID;

import blueprint.workflowmodule.loanapproval.api.LoanApprovalApi;
import blueprint.workflowmodule.loanapproval.api.model.LoanRequest;
import blueprint.workflowmodule.loanapproval.api.model.OpenAssessment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. Every request a person types is a GET, so one loan approval can be
 * walked through in a browser - no tooling, no request bodies. The one POST is for the front end
 * of a customer, which hands in documents with the request.
 *
 * <p>
 * It talks to {@link Service} and to nothing else. That the use case happens to be
 * implemented by a BPMN process is none of its business.
 * </p>
 *
 * <p>
 * The paths, the parameters and the answers come from {@link LoanApprovalApi}, the interface
 * the build generates from {@code loan-approval-api/loan-approval.yaml}. This class only
 * implements it. The simulator of this blueprint calls the same API, with the client generated
 * from the same file.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements LoanApprovalApi {

  @Inject
  Service loanApproval;

  /**
   * Starts one loan approval.
   *
   * @param amount The amount requested.
   * @return The id of the loan request started.
   */
  @Override
  public String start(
      final Integer amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, amount, "");

    log.info(
        "Show the result -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * A customer requests a loan and hands in documents with it.
   *
   * @param loanRequest The amount and the documents.
   * @return The id of the loan request started.
   */
  @Override
  public String request(
      final LoanRequest loanRequest) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, loanRequest.getAmount(), loanRequest.getDocuments());

    return loanRequestId;

  }

  /**
   * The assessments which wait for a clerk, as a task list shows them.
   *
   * @return Up to 50 open assessments.
   */
  @Override
  public List<OpenAssessment> openAssessments() {

    return loanApproval
        .openAssessments()
        .stream()
        .map(assessment -> new OpenAssessment(assessment.getLoanRequestId(), assessment.getAssessmentTaskId()))
        .toList();

  }

  /**
   * How many loan approvals have not reached an end yet, as an operator asks.
   *
   * @return The number of unfinished loan approvals.
   */
  @Override
  public Long unfinished() {

    return loanApproval.unfinished();

  }

  /**
   * A clerk decides about a loan.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param taskId        The id of the user task, which the log names.
   * @param approved      The decision.
   * @return A sentence for the browser.
   */
  @Override
  public String assess(
      final String loanRequestId,
      final String taskId,
      final Boolean approved) {

    loanApproval.assess(loanRequestId, taskId, approved);

    return "Loan approval '"
        + loanRequestId
        + "' was assessed";

  }

  /**
   * Shows what the process did, which is the second half of operating it in a browser.
   *
   * @param loanRequestId The id returned by starting the process.
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

}
