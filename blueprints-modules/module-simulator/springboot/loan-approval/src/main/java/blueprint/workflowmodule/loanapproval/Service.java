package blueprint.workflowmodule.loanapproval;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.esignature.ContractSigning;
import blueprint.externalsystems.esignature.ContractToSign;
import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
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
 */
@Slf4j
@org.springframework.stereotype.Service
public class Service {

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  @Autowired
  private LoanApprovalProperties properties;

  @Autowired
  private CreditRating creditRating;

  @Autowired
  private ContractSigning contractSigning;

  /**
   * A customer requests a loan. The credit rating is asked at once, while the customer waits,
   * and only a customer with a good enough score gets a workflow.
   *
   * <p>
   * There is no {@code @Transactional} here, on purpose. The credit rating is asked outside of a
   * transaction, so no database connection waits for it. {@link Workflow} opens the transaction
   * which stores the aggregate and starts the workflow.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @param customerId    The customer asking for the loan.
   * @param amount        The amount requested.
   * @return {@code true} if the workflow started, {@code false} if the loan was declined.
   * @throws blueprint.externalsystems.creditrating.CreditRatingUnavailableException If the credit
   *                                                                                 rating does
   *                                                                                 not answer.
   */
  public boolean request(
      final String loanRequestId,
      final String customerId,
      final int amount) {

    final var score = creditRating.scoreOf(customerId);
    if (score.isEmpty()) {
      log.info(
          "Loan request '{}' declined: the credit rating does not know customer '{}'",
          loanRequestId,
          customerId);
      return false;
    }
    if (score.get().value() < properties.getMinimumCreditScore()) {
      log.info(
          "Loan request '{}' declined: customer '{}' has a credit score of {}, below {}",
          loanRequestId,
          customerId,
          score.get().value(),
          properties.getMinimumCreditScore());
      return false;
    }

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .customerId(customerId)
        .amount(amount)
        .creditScore(score.get().value())
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
   * The user task "Prepare contract" was created. The Business Cockpit shows it as well, because
   * the cockpit adapter reports it there.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task.
   */
  public void contractPreparationOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setPrepareContractTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for its contract. Continue with:"
            + "\n  Prepare the contract -> http://localhost:8080/api/loan-approval/{}/prepare-contract/{}?interestRate=3.5",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId(),
        taskId);

  }

  /**
   * Somebody prepared the contract, which completes the user task.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task "Prepare contract".
   * @param interestRate  The interest rate written into the contract, in percent.
   */
  @Transactional
  public void prepareContract(
      final String loanRequestId,
      final String taskId,
      final BigDecimal interestRate) {

    final var loanApproval = loanApprovals
        .findById(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));
    if (!taskId.equals(loanApproval.getPrepareContractTaskId())) {
      throw new IllegalStateException("The contract of loan approval '"
          + loanRequestId
          + "' is not waiting for task '"
          + taskId
          + "'");
    }

    loanApproval.setInterestRate(interestRate);
    workflow.contractPrepared(loanApproval, taskId);
    // The task is answered, so the id does not lead to an open task any more.
    loanApproval.setPrepareContractTaskId(null);

    log.info(
        "Contract of loan approval '{}' prepared at {} %",
        loanRequestId,
        interestRate);

  }

  /**
   * Hands the contract to the e-signature service. The signature comes later, through
   * {@link #contractSigned(String)}.
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
        loanApproval.getLoanRequestId(), loanApproval.getCustomerId(), "Loan of "
            + loanApproval.getAmount()
            + " at "
            + loanApproval.getInterestRate()
            + " % interest"));

    loanApproval.setContractSentForSigning(true);

    log.info(
        "The contract of loan approval '{}' waits for the signature of customer '{}'",
        loanApproval.getLoanRequestId(),
        loanApproval.getCustomerId());

  }

  /**
   * The customer signed the contract. A report which arrives twice is taken once.
   *
   * @param loanRequestId The natural id of the loan request, which is the id of the contract.
   * @return {@code false} if there is no loan approval of this id.
   */
  @Transactional
  public boolean contractSigned(
      final String loanRequestId) {

    final var found = loanApprovals.findById(loanRequestId);
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
    workflow.contractSigned(loanApproval);

    log.info(
        "Loan approval '{}' approved, the customer signed. Show it -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId,
        loanRequestId);

    return true;

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> get(
      final String loanRequestId) {

    return loanApprovals.findById(loanRequestId);

  }

}
