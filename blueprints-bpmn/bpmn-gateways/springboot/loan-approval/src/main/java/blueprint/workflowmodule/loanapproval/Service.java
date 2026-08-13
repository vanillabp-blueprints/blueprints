package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells {@link Workflow}
 * what happened, {@code loanRequested} rather than "start the process", and that class
 * decides what this means for the BPMN. The other direction runs through
 * {@link WorkflowTaskHandler}, which calls the methods below when the process reaches a
 * task.
 * </p>
 *
 * <p>
 * Both directions meet here, and that is the point: this is the one class describing the
 * use case, and it does so without naming a single BPMN element.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the method the API calls, because
 * starting a workflow has to run in a transaction. It is deliberately absent from the
 * methods a task handler calls: VanillaBP already runs a task in a transaction it owns,
 * and it commits that transaction for a {@code TaskException} on purpose. A transaction
 * declared here would roll back instead and throw away what the handler wrote for the
 * process to react to. VanillaBP sees the transaction it can no longer commit and fails the
 * task naming it, so the mistake shows up rather than costing data.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service
@EnableConfigurationProperties(LoanApprovalProperties.class)
public class Service {

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   */
  @Transactional
  public void initiateLoanApproval(
      final String loanRequestId,
      final int amount) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .build();

    workflow.loanRequested(loanApproval);

    log.info("Loan approval '{}' started", loanRequestId);

  }

  /**
   * Rates a loan request and decides what that rating means. The decision is two booleans
   * on the aggregate, and the gateway of the process reads exactly those.
   *
   * <p>
   * The comparison happens here rather than in the BPMN on purpose. A condition like
   * "rating of at least 30" in the model looks harmless until the number has to change:
   * then it is a new process version, deployed, with running instances on the old one.
   * Here it is a line of configuration. What the model keeps is the routing, which is
   * what a reader of the diagram wants to see.
   * </p>
   *
   * <p>
   * The result is ONE attribute holding one of three values, not a boolean per branch.
   * Two conditions that can be true at the same time leave the choice to the engine, and
   * engines answer that differently - which is a bug nobody sees until the process runs
   * on the other one.
   * </p>
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);
    loanApproval.setRatingBand(bandOf(rating));

    log.info(
        "Credit rating of loan approval '{}' is {}, which counts as '{}'",
        loanApproval.getLoanRequestId(),
        rating,
        loanApproval.getRatingBand());

  }

  /**
   * The loan was approved, which is the branch the gateway takes for an acceptable
   * rating.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void approveLoan(
      final Aggregate loanApproval) {

    loanApproval.setOutcome("approved");

    log.info("Loan approval '{}' was approved", loanApproval.getLoanRequestId());

  }

  /**
   * A person has to look at the request, which is the middle branch.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void requestManualReview(
      final Aggregate loanApproval) {

    loanApproval.setOutcome("under-review");

    log.info(
        "Loan approval '{}' goes to a manual review",
        loanApproval.getLoanRequestId());

  }

  /**
   * The loan was rejected, which is the branch taken when no condition holds - the
   * default flow of the gateway.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void rejectLoan(
      final Aggregate loanApproval) {

    loanApproval.setOutcome("rejected");

    log.info("Loan approval '{}' was rejected", loanApproval.getLoanRequestId());

  }

  /**
   * The customer gets a letter, which is the branch the second gateway takes for a large
   * amount.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void sendPaperLetter(
      final Aggregate loanApproval) {

    loanApproval.setNotifiedBy("letter");

    log.info(
        "The customer of loan approval '{}' gets a paper letter",
        loanApproval.getLoanRequestId());

  }

  /**
   * The customer gets an email, which is the default flow of the second gateway.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void sendEmail(
      final Aggregate loanApproval) {

    loanApproval.setNotifiedBy("email");

    log.info(
        "The customer of loan approval '{}' gets an email",
        loanApproval.getLoanRequestId());

  }

  /**
   * What a rating counts as. The three values are the vocabulary the BPMN routes on, so
   * they are part of the contract with the model - renaming one means touching both.
   *
   * @param rating The credit rating.
   * @return The band the rating falls into.
   */
  private String bandOf(
      final int rating) {

    if (rating >= properties.getMinimumRating()) {
      return "acceptable";
    }
    if (rating >= properties.getManualReviewRating()) {
      return "review";
    }
    return "too-low";

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    return loanApprovals.findById(loanRequestId);

  }

}
