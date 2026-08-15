package blueprint.workflowmodule.loanapproval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.PartnerOffer;
import blueprint.workflowmodule.loanapproval.model.RegionResult;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells {@link Workflow}
 * what happened, and the other direction runs through {@link WorkflowTaskHandler}, which
 * calls the methods below when the process reaches a task.
 * </p>
 *
 * <p>
 * Note what the nesting looks like from here: {@link #requestPartnerOffer} takes one region
 * and one partner, and nothing in it says that two loops of a BPMS produced that pair. The
 * model decides how often this runs, the business code decides what one run does.
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
   * Rates a loan request and decides which regions are assessed and which partners are
   * asked in each of them. Both collections are what the model iterates over, so both have
   * to be on the aggregate before the process gets to the subprocess.
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);
    // mutable lists on purpose: JPA owns them once the aggregate is saved, and an
    // immutable one makes Hibernate fail while merging the entity
    loanApproval.setRegionIds(new ArrayList<>(properties
        .getRegions()
        .stream()
        .map(LoanApprovalProperties.Region::getId)
        .toList()));
    loanApproval.setPartnerIds(new ArrayList<>(properties
        .getPartners()
        .stream()
        .map(LoanApprovalProperties.Partner::getId)
        .toList()));

    log.info(
        "Credit rating of loan approval '{}' is {}, asking {} partner(s) in {} region(s)",
        loanApproval.getLoanRequestId(),
        rating,
        loanApproval
            .getPartnerIds()
            .size(),
        loanApproval
            .getRegionIds()
            .size());

  }

  /**
   * Asks one partner for an offer in one region. This method is the innermost point of the
   * two loops, and it knows nothing about either.
   *
   * @param loanApproval The workflow's aggregate.
   * @param regionId     The region asked for.
   * @param partnerId    The partner asked.
   */
  public void requestPartnerOffer(
      final Aggregate loanApproval,
      final String regionId,
      final String partnerId) {

    final var partner = properties
        .getPartners()
        .stream()
        .filter(candidate -> candidate
            .getId()
            .equals(partnerId))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "No partner '"
                + partnerId
                + "' is configured. Check 'loan-approval.partners'."));
    final var region = properties
        .getRegions()
        .stream()
        .filter(candidate -> candidate
            .getId()
            .equals(regionId))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "No region '"
                + regionId
                + "' is configured. Check 'loan-approval.regions'."));

    final var rate = partner.getSpread() + region.getSurcharge() + Math.max(0, 100 - loanApproval.getCreditRating());

    loanApproval.addOffer(PartnerOffer
        .builder()
        .regionId(regionId)
        .partnerId(partnerId)
        .rate(rate)
        .build());

    log.info(
        "Partner '{}' offers {} basis points in region '{}' for loan approval '{}'",
        partnerId,
        rate,
        regionId,
        loanApproval.getLoanRequestId());

  }

  /**
   * Picks the best offer of ONE region. Called once per iteration of the subprocess, after
   * that region's offers are in - a result over the inner loop belongs at the end of the
   * outer one.
   *
   * @param loanApproval The workflow's aggregate.
   * @param regionId     The region to summarise.
   * @param iteration    Which iteration of the subprocess this is.
   */
  public void summariseRegion(
      final Aggregate loanApproval,
      final String regionId,
      final int iteration) {

    final var best = loanApproval
        .getOffers()
        .stream()
        .filter(offer -> regionId.equals(offer.getRegionId()))
        .min(Comparator.comparing(PartnerOffer::getRate))
        .orElseThrow(() -> new IllegalStateException(
            "Region '"
                + regionId
                + "' of loan approval '"
                + loanApproval.getLoanRequestId()
                + "' has no offers at all, although its assessment is finishing."));

    loanApproval.addRegionResult(RegionResult
        .builder()
        .regionId(regionId)
        .iteration(iteration)
        .bestPartnerId(best.getPartnerId())
        .bestRate(best.getRate())
        .build());

    log.info(
        "Region '{}' of loan approval '{}' settles on '{}' at {} basis points",
        regionId,
        loanApproval.getLoanRequestId(),
        best.getPartnerId(),
        best.getRate());

  }

  /**
   * Picks the best of the regions. Runs once, when the last iteration of the subprocess has
   * finished.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void chooseBestOffer(
      final Aggregate loanApproval) {

    final var best = loanApproval
        .getRegionResults()
        .stream()
        .min(Comparator.comparing(RegionResult::getBestRate))
        .orElseThrow(() -> new IllegalStateException(
            "Loan approval '"
                + loanApproval.getLoanRequestId()
                + "' has no region results, although every region was assessed."));

    loanApproval.setChosenRegionId(best.getRegionId());
    loanApproval.setChosenPartnerId(best.getBestPartnerId());
    loanApproval.setChosenRate(best.getBestRate());

    log.info(
        "Loan approval '{}' takes '{}' in region '{}' at {} basis points, out of {} region(s)",
        loanApproval.getLoanRequestId(),
        best.getBestPartnerId(),
        best.getRegionId(),
        best.getBestRate(),
        loanApproval
            .getRegionResults()
            .size());

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
