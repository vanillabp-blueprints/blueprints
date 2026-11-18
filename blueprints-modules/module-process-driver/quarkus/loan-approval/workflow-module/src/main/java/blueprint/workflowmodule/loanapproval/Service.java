package blueprint.workflowmodule.loanapproval;

import java.util.Optional;
import java.util.function.Function;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.esignature.ContractSigning;
import blueprint.externalsystems.esignature.ContractToSign;
import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It talks to two systems around the module, and it names them by their role:
 * {@code creditRating} and {@code contractSigning}. Both are ports. Which product answers, and
 * whether it is the simulator or the real system, this class cannot tell, and it does not have
 * to.
 * </p>
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method called
 * by a workflow task already runs in the transaction VanillaBP opened for it and joins it, but
 * the annotation still shows that the method needs one.
 * </p>
 *
 * <p>
 * No method here throws a {@code TaskException}. One which does, so the process can react,
 * needs {@code @Transactional(dontRollbackOn = TaskException.class)}: VanillaBP commits its
 * transaction for that exception, and a plain {@code @Transactional} would mark it for rollback.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Transactional
public class Service {

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  Workflow workflow;

  @Inject
  LoanApprovalProperties properties;

  @Inject
  CreditRating creditRating;

  @Inject
  ContractSigning contractSigning;

  /**
   * A customer requests a loan. The credit rating is asked at once, while the customer waits,
   * and only a customer with a good enough score gets a workflow.
   *
   * <p>
   * The credit rating is asked inside the transaction, so a database connection waits for its
   * answer. The adapter gives up after a short timeout, which keeps that wait short. A system
   * which may take long is asked before the transaction starts, in a class of its own.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @param customerId    The customer asking for the loan.
   * @param customerName  The name of the customer, who signs the contract.
   * @param amount        The amount requested.
   * @return {@code true} if the workflow started, {@code false} if the loan was declined.
   * @throws blueprint.externalsystems.creditrating.CreditRatingUnavailableException If the credit
   *                                                                                 rating does
   *                                                                                 not answer.
   */
  public boolean request(
      final String loanRequestId,
      final String customerId,
      final String customerName,
      final int amount) {

    final var score = creditRating.scoreOf(customerId);
    if (score.isEmpty()) {
      log.info(
          "Loan request '{}' declined: the credit rating does not know customer '{}'",
          loanRequestId,
          customerId);
      return false;
    }
    if (score.get().value() < properties.minimumCreditScore()) {
      log.info(
          "Loan request '{}' declined: customer '{}' has a credit score of {}, below {}",
          loanRequestId,
          customerId,
          score.get().value(),
          properties.minimumCreditScore());
      return false;
    }

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .customerId(customerId)
        .customerName(customerName)
        .amount(amount)
        .creditScore(score.get().value())
        .riskAssessmentNeeded(amount >= properties.riskAssessmentLimit())
        .build();

    workflow.loanRequested(loanApproval);

    log.info(
        "Loan approval '{}' started: customer '{}' asks for {} with a credit score of {}",
        loanRequestId,
        customerId,
        amount,
        score.get().value());

    return true;

  }

  /**
   * The user task "Assess risk" was created. The Business Cockpit shows it as well, because the
   * cockpit adapter reports it there.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task.
   */
  public void riskAssessmentOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setAssessRiskTaskId(taskId);

    log.info(
        "Loan approval '{}' asks for {}, which needs a risk assessment. Continue with:"
            + "\n  Assess the risk -> http://localhost:8080/loan-approval/api/{}/assess-risk/{}",
        loanApproval.getLoanRequestId(),
        loanApproval.getAmount(),
        loanApproval.getLoanRequestId(),
        taskId);

  }

  /**
   * Somebody assessed the risk, which completes the user task.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task "Assess risk".
   */
  public void assessRisk(
      final String loanRequestId,
      final String taskId) {

    final var loanApproval = loanApprovalWaitingFor(loanRequestId, taskId, Aggregate::getAssessRiskTaskId);

    loanApproval.setRiskAssessed(true);
    workflow.riskAssessed(loanApproval, taskId);
    // The task is answered, so the id does not lead to an open task any more.
    loanApproval.setAssessRiskTaskId(null);

    log.info("Risk of loan approval '{}' assessed", loanRequestId);

  }

  /**
   * Hands the contract to the e-signature service. The answer of the customer comes later,
   * through {@link #contractSigned(String)} or {@link #contractDeclined(String)}.
   *
   * <p>
   * The contract carries the id of the loan request. So the callback finds the loan approval,
   * and a second attempt after a failure replaces the first contract instead of adding one.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void sendContractForSigning(
      final Aggregate loanApproval) {

    contractSigning.sendForSigning(new ContractToSign(
        loanApproval.getLoanRequestId(), loanApproval.getCustomerName(), "Loan of "
            + loanApproval.getAmount()
            + " for customer "
            + loanApproval.getCustomerId()));

    loanApproval.setContractSentForSigning(true);

    log.info(
        "The contract of loan approval '{}' waits for the signature of '{}'",
        loanApproval.getLoanRequestId(),
        loanApproval.getCustomerName());

  }

  /**
   * The user task "Check contract" was created. The signature may still be on its way.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task.
   */
  public void contractCheckOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setCheckContractTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for the check of its signed contract. Continue with:"
            + "\n  Check the contract -> http://localhost:8080/loan-approval/api/{}/check-contract/{}",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId(),
        taskId);

  }

  /**
   * The customer signed the contract. A report which arrives twice is taken once.
   *
   * <p>
   * The signature is a fact of the business case, so it is written to the aggregate. It is not
   * a message to the process: the process waits in "Check contract", whether the signature is
   * there or not.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request, which is the id of the contract.
   * @return {@code false} if there is no loan approval of this id.
   */
  public boolean contractSigned(
      final String loanRequestId) {

    final var found = loanApprovals.findByIdOptional(loanRequestId);
    if (found.isEmpty()) {
      log.warn("A signature arrived for loan approval '{}', which does not exist", loanRequestId);
      return false;
    }

    final var loanApproval = found.get();
    if (Boolean.TRUE.equals(loanApproval.getContractSigned())) {
      log.info("The signature of loan approval '{}' arrived again", loanRequestId);
      return true;
    }

    loanApproval.setContractSigned(true);

    log.info("The customer signed the contract of loan approval '{}'", loanRequestId);

    return true;

  }

  /**
   * The customer declined to sign the contract. There is nothing left to check, so the workflow
   * takes the task "Check contract" away, and the loan is declined. A report which arrives twice
   * is taken once.
   *
   * <p>
   * Unlike the signature, the decline is a message to the process, because it changes the way
   * the workflow goes. The fact is written to the aggregate first, and then the message is
   * correlated, in the same transaction.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request, which is the id of the contract.
   * @return {@code false} if there is no loan approval of this id.
   */
  public boolean contractDeclined(
      final String loanRequestId) {

    final var found = loanApprovals.findByIdOptional(loanRequestId);
    if (found.isEmpty()) {
      log.warn("A decline arrived for loan approval '{}', which does not exist", loanRequestId);
      return false;
    }

    final var loanApproval = found.get();
    if (Boolean.TRUE.equals(loanApproval.getContractDeclined())) {
      log.info("The decline of loan approval '{}' arrived again", loanRequestId);
      return true;
    }

    loanApproval.setContractDeclined(true);
    workflow.contractDeclined(loanApproval);

    log.info("The customer declined to sign the contract of loan approval '{}'", loanRequestId);

    return true;

  }

  /**
   * The workflow took the user task "Check contract" away. The model does this when the customer
   * declined to sign, and then the loan is declined and the workflow ends. An operator who stops
   * the workflow in the BPMS takes the task away as well, but declines no loan.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void contractCheckClosed(
      final Aggregate loanApproval) {

    // The task is gone, so the id does not lead to an open task any more.
    loanApproval.setCheckContractTaskId(null);
    if (!Boolean.TRUE.equals(loanApproval.getContractDeclined())) {
      return;
    }

    loanApproval.setLoanDeclined(true);

    log.info(
        "Loan approval '{}' declined, because the customer declined to sign. Show it ->"
            + " http://localhost:8080/loan-approval/api/{}",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId());

  }

  /**
   * Somebody checked the signed contract, which completes the user task and approves the loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task "Check contract".
   * @throws ContractNotSignedException If the signature has not arrived yet. The task stays
   *                                    open.
   */
  public void checkContract(
      final String loanRequestId,
      final String taskId) {

    final var loanApproval = loanApprovalWaitingFor(loanRequestId, taskId, Aggregate::getCheckContractTaskId);
    if (!Boolean.TRUE.equals(loanApproval.getContractSigned())) {
      throw new ContractNotSignedException(loanRequestId);
    }

    loanApproval.setContractChecked(true);
    workflow.contractChecked(loanApproval, taskId);
    loanApproval.setCheckContractTaskId(null);

    log.info(
        "Loan approval '{}' approved. Show it -> http://localhost:8080/loan-approval/api/{}",
        loanRequestId,
        loanRequestId);

  }

  private Aggregate loanApprovalWaitingFor(
      final String loanRequestId,
      final String taskId,
      final Function<Aggregate, String> openTaskId) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));
    if (!taskId.equals(openTaskId.apply(loanApproval))) {
      throw new IllegalStateException("Loan approval '"
          + loanRequestId
          + "' does not wait for task '"
          + taskId
          + "'");
    }
    return loanApproval;

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> get(
      final String loanRequestId) {

    return loanApprovals.findByIdOptional(loanRequestId);

  }

}
