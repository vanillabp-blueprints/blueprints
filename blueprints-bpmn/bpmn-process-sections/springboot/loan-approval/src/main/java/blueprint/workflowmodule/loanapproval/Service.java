package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.DocumentCheck;
import blueprint.workflowmodule.loanapproval.model.RiskAssessment;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells {@link Workflow}
 * what happened, {@code loanRequested} rather than "start the process", and that class
 * decides what this means for the BPMN. The other direction runs through the task handlers,
 * which call the methods below when a process reaches a task.
 * </p>
 *
 * <p>
 * The methods of a section come in a fixed shape: the first one of the section creates the
 * section's object, the later ones fill it in. Nothing outside a section writes its object,
 * and nothing outside a section reads it without a null check.
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
   * Rates a loan request and decides whether the risk people have to see it. A real
   * application would ask a rating service here.
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);
    loanApproval.setLargeExposure(loanApproval.getAmount() >= properties.getRiskAssessmentFrom());

    log.info(
        "Credit rating of loan approval '{}' is {}, risk assessment {}",
        loanApproval.getLoanRequestId(),
        rating,
        loanApproval.isRiskAssessmentRequired() ? "required" : "not required");

  }

  /**
   * The first step of the document check. It creates the object this section works on, and
   * from here on every later step of the section may rely on it.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void collectDocuments(
      final Aggregate loanApproval) {

    // A big loan needs one document more than the usual application, and that one is
    // never in the first batch.
    final var requiredDocuments = properties.getDocumentsExpected() + (loanApproval.isRiskAssessmentRequired() ? 1 : 0);

    loanApproval.setDocumentCheck(
        DocumentCheck
            .builder()
            .documentsRequired(requiredDocuments)
            .documentsReceived(properties.getDocumentsExpected())
            .build());

    log.info(
        "Loan approval '{}' received {} of {} documents",
        loanApproval.getLoanRequestId(),
        properties.getDocumentsExpected(),
        requiredDocuments);

  }

  /**
   * The second step of the document check. It fills in the object the first step created.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void verifySignatures(
      final Aggregate loanApproval) {

    final var documentCheck = loanApproval.getDocumentCheck();
    final var complete = documentCheck.getDocumentsReceived() >= documentCheck.getDocumentsRequired();

    documentCheck.setSignaturesComplete(complete);

    log.info(
        "Signatures of loan approval '{}' are {}",
        loanApproval.getLoanRequestId(),
        complete ? "complete" : "incomplete");

  }

  /**
   * The first step of the risk assessment, which creates the object of that section. A loan
   * below the configured limit never gets here, and its aggregate keeps no risk assessment
   * at all.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void checkCollateral(
      final Aggregate loanApproval) {

    final var amount = loanApproval.getAmount();
    final var collateral = Math.min(
        (amount * properties.getCollateralPercentage()) / 100,
        properties.getMaximumCollateral());
    final var requiredCollateral = (amount * properties.getMinimumCollateralPercentage()) / 100;

    loanApproval.setRiskAssessment(
        RiskAssessment
            .builder()
            .collateralValue(collateral)
            .collateralSufficient(collateral >= requiredCollateral)
            .build());

    log.info(
        "Collateral of loan approval '{}' is worth {}, needed are {}",
        loanApproval.getLoanRequestId(),
        collateral,
        requiredCollateral);

  }

  /**
   * The second step of the risk assessment.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void checkDebtRatio(
      final Aggregate loanApproval) {

    final var debtRatio = Math.min(100, loanApproval.getAmount() / 500);

    loanApproval
        .getRiskAssessment()
        .setDebtRatio(debtRatio);

    log.info(
        "Debt ratio of loan approval '{}' is {}%",
        loanApproval.getLoanRequestId(),
        debtRatio);

  }

  /**
   * Decides a loan the risk people looked at. Reached only when the second section ran, so
   * its object is there.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void decideWithRiskReport(
      final Aggregate loanApproval) {

    final var riskAssessment = loanApproval.getRiskAssessment();
    final var debtIsBearable = riskAssessment.getDebtRatio() <= properties.getMaximumDebtRatio();
    final var acceptable = loanApproval.isCollateralSufficient() && debtIsBearable;

    loanApproval.setDecision(acceptable ? "approved" : "rejected");

    log.info(
        "Loan approval '{}' was {} (collateral {}, debt ratio {}%)",
        loanApproval.getLoanRequestId(),
        loanApproval.getDecision(),
        riskAssessment.getCollateralValue(),
        riskAssessment.getDebtRatio());

  }

  /**
   * Decides a loan nobody assessed the risk of. The risk assessment is {@code null} here,
   * and this method is written so that it never has to look.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void decideOnDocumentsAlone(
      final Aggregate loanApproval) {

    final var ratingIsGoodEnough = loanApproval.getCreditRating() >= properties.getMinimumRating();
    final var acceptable = loanApproval.isSignaturesComplete() && ratingIsGoodEnough;

    loanApproval.setDecision(acceptable ? "approved" : "rejected");

    log.info(
        "Loan approval '{}' was {} on its documents, without a risk report",
        loanApproval.getLoanRequestId(),
        loanApproval.getDecision());

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
