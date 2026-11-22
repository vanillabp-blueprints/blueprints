package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import blueprint.externalsystems.creditrating.CreditRatingUnavailableException;
import blueprint.workflowmodule.loanapproval.api.server.LoanApprovalApi;
import blueprint.workflowmodule.loanapproval.api.server.model.Answer;
import blueprint.workflowmodule.loanapproval.api.server.model.LoanRequest;
import blueprint.workflowmodule.loanapproval.api.server.model.Unavailable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. A person walks through the process in a browser with its GET
 * requests. No tooling, no request bodies. The forms of the user tasks complete them with POST on
 * the same paths, as forms do, see CONTRIBUTING.md. Both ways end in the same call of
 * {@link Service}.
 *
 * <p>
 * It talks to {@link Service} and to nothing else. When a system around the module does not
 * answer while a user waits, the user gets a 503 which names the role of that system.
 * </p>
 *
 * <p>
 * The paths, the parameters and the classes {@link Answer} and {@link Unavailable} come from {@link
 * LoanApprovalApi}, the interface the module {@code loan-approval-gui-api-server} generates from
 * {@code loan-approval/gui-api/loan-approval.yaml}. This class only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements LoanApprovalApi {

  /** The media type of a problem detail. */
  private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

  @Inject
  Service loanApproval;

  /**
   * Requests a loan. This is the URL the README names.
   *
   * @param customerId   The customer asking for the loan.
   * @param customerName The name of the customer, who signs the contract.
   * @param amount       The amount requested.
   * @return Whether a workflow started for the loan request.
   */
  @Override
  public Answer request(
      final String customerId,
      final String customerName,
      final Integer amount) {

    final var loanRequestId = UUID
        .randomUUID()
        .toString();

    if (!loanApproval.request(loanRequestId, customerId, customerName, amount)) {
      return new Answer(loanRequestId, "declined");
    }

    log.info(
        "Show the loan approval -> http://localhost:8080/loan-approval/api/{}",
        loanRequestId);

    return new Answer(loanRequestId, "started");

  }

  /**
   * Completes the user task "Assess risk".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task, as the log and the cockpit show it.
   * @return A confirmation.
   */
  @Override
  public String assessRisk(
      final String loanRequestId,
      final String taskId) {

    loanApproval.assessRisk(loanRequestId, taskId);

    return "Risk of loan approval '"
        + loanRequestId
        + "' assessed";

  }

  /**
   * Completes the user task "Check contract", once the customer signed the contract.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task, as the log and the cockpit show it.
   * @return A confirmation.
   */
  @Override
  public String checkContract(
      final String loanRequestId,
      final String taskId) {

    loanApproval.checkContract(loanRequestId, taskId);

    return "Loan approval '"
        + loanRequestId
        + "' approved";

  }

  /**
   * Completes the user task "Assess risk". The form of the task calls this one.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task, as the log and the cockpit show it.
   * @return A confirmation.
   */
  @Override
  public String completeAssessRisk(
      final String loanRequestId,
      final String taskId) {

    return assessRisk(loanRequestId, taskId);

  }

  /**
   * Completes the user task "Check contract", once the customer signed the contract. The form of
   * the task calls this one.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task, as the log and the cockpit show it.
   * @return A confirmation.
   */
  @Override
  public String completeCheckContract(
      final String loanRequestId,
      final String taskId) {

    return checkContract(loanRequestId, taskId);

  }

  /**
   * Shows what the process did.
   *
   * @param loanRequestId The id returned by the loan request.
   * @return The loan request as it is stored right now.
   * @throws NotFoundException With 404, if there is no loan request of this id.
   */
  @Override
  public LoanRequest show(
      final String loanRequestId) {

    return loanApproval
        .get(loanRequestId)
        // The generator of this platform writes a constructor of the required fields only. The
        // others say how far the process came, and each may still be missing.
        .map(loanRequest -> new LoanRequest(
            loanRequest.getLoanRequestId(), loanRequest.getCustomerId(), loanRequest.getCustomerName(), loanRequest
                .getAmount(), loanRequest.getCreditScore(), loanRequest.isRiskAssessmentNeeded())
            .assessRiskTaskId(loanRequest.getAssessRiskTaskId())
            .riskAssessed(loanRequest.getRiskAssessed())
            .contractSentForSigning(loanRequest.getContractSentForSigning())
            .checkContractTaskId(loanRequest.getCheckContractTaskId())
            .contractSigned(loanRequest.getContractSigned())
            .contractChecked(loanRequest.getContractChecked())
            .contractDeclined(loanRequest.getContractDeclined())
            .loanDeclined(loanRequest.getLoanDeclined()))
        .orElseThrow(() -> new NotFoundException("Unknown loan request '"
            + loanRequestId
            + "'"));

  }

  /**
   * The contract is not signed yet, so the user task "Check contract" cannot be completed. It
   * stays open, and the user may try again later.
   *
   * @param e What the business code reported.
   * @return The reason, with status 409.
   */
  @ServerExceptionMapper(ContractNotSignedException.class)
  public RestResponse<String> contractNotSigned(
      final ContractNotSignedException e) {

    log.info("{}", e.getMessage());

    return RestResponse.ResponseBuilder
        .create(RestResponse.Status.CONFLICT, e.getMessage())
        .type(MediaType.TEXT_PLAIN_TYPE)
        .build();

  }

  /**
   * The credit rating does not answer while the user waits for it. The answer says which system
   * is missing, by its role, so the user knows it is not the application which failed. The
   * product behind the role stays inside the adapter.
   *
   * @param e What the adapter reported.
   * @return A problem detail with status 503.
   */
  @ServerExceptionMapper(CreditRatingUnavailableException.class)
  public RestResponse<Unavailable> creditRatingUnavailable(
      final CreditRatingUnavailableException e) {

    log.warn("{}", e.getMessage());

    return RestResponse.ResponseBuilder
        .create(
            RestResponse.Status.SERVICE_UNAVAILABLE,
            new Unavailable(
                RestResponse.Status.SERVICE_UNAVAILABLE
                    .getStatusCode(), "Service Unavailable", "The credit rating does not answer. Try again later.", CreditRatingUnavailableException.ROLE))
        .type(MediaType.valueOf(APPLICATION_PROBLEM_JSON))
        .build();

  }

}
