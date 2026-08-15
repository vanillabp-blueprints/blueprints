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
 * Note what the iteration looks like from here: {@link #requestPartnerOffer} is a method
 * taking one partner, and nothing in it says that a BPMS runs it several times. A loop over
 * all partners would look exactly the same from the outside, which is the point - the model
 * decides how often this runs, the business code decides what one run does.
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
   * Rates a loan request and decides which partners are asked for an offer. The list of
   * partners is what the multi-instance task iterates over, so it has to be on the
   * aggregate before the process gets there.
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);
    // A mutable list on purpose: JPA owns this collection once the aggregate is saved, and
    // an immutable one makes Hibernate fail while merging the entity.
    loanApproval.setPartnerIds(new ArrayList<>(properties
        .getPartners()
        .stream()
        .map(LoanApprovalProperties.Partner::getId)
        .toList()));

    log.info(
        "Credit rating of loan approval '{}' is {}, asking {} partner(s)",
        loanApproval.getLoanRequestId(),
        rating,
        loanApproval
            .getPartnerIds()
            .size());

  }

  /**
   * Asks one partner for an offer. A real application would call that partner here; what
   * matters for the blueprint is that this method knows about ONE partner, not about the
   * iteration it is part of.
   *
   * @param loanApproval The workflow's aggregate.
   * @param partnerId    The partner to ask.
   * @param iteration    Which iteration this is, as the BPMS counts it.
   */
  public void requestPartnerOffer(
      final Aggregate loanApproval,
      final String partnerId,
      final int iteration) {

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
                + "' is configured, although the process was asked to request an offer from"
                + " it. Check 'loan-approval.partners'."));

    final var rate = partner.getSpread() + Math.max(0, 100 - loanApproval.getCreditRating());

    loanApproval.addOffer(PartnerOffer
        .builder()
        .partnerId(partnerId)
        .iteration(iteration)
        .rate(rate)
        .build());

    log.info(
        "Partner '{}' offers {} basis points for loan approval '{}'",
        partnerId,
        rate,
        loanApproval.getLoanRequestId());

  }

  /**
   * Picks the best of the offers. This runs once, after the last iteration, and it is the
   * place a result over all iterations belongs - an iteration itself sees one element and
   * runs next to its siblings.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void chooseBestOffer(
      final Aggregate loanApproval) {

    final var best = loanApproval
        .getOffers()
        .stream()
        .min(Comparator.comparing(PartnerOffer::getRate))
        .orElseThrow(() -> new IllegalStateException(
            "Loan approval '"
                + loanApproval.getLoanRequestId()
                + "' has no offers at all, although the task following the multi-instance"
                + " task was reached."));

    loanApproval.setChosenPartnerId(best.getPartnerId());
    loanApproval.setChosenRate(best.getRate());

    log.info(
        "Loan approval '{}' takes the offer of '{}' at {} basis points, out of {} offer(s)",
        loanApproval.getLoanRequestId(),
        best.getPartnerId(),
        best.getRate(),
        loanApproval
            .getOffers()
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
