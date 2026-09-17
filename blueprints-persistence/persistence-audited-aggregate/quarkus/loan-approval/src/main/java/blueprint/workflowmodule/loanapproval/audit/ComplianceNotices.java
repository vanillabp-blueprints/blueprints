package blueprint.workflowmodule.loanapproval.audit;

import java.util.Map;
import java.util.Optional;

import blueprint.workflowmodule.loanapproval.ComplianceArchive;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.integration.spi.PhaseOperation;
import io.vanillabp.integration.spi.PhaseOperationRegistry;
import io.vanillabp.integration.spi.PhaseTwoCall;
import io.vanillabp.integration.spi.PhaseTwoOutbox;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Reporting a decision to the compliance archive, crash-safe and about the right state.
 *
 * <p>
 * The report may not get lost, and it may not be sent for a decision which was rolled
 * back afterwards, so it goes through the outbox VanillaBP already runs: the notice is
 * written down inside the transaction of the decision and sent once that transaction
 * committed. An application may put operations of its own into that outbox, and this is
 * one - the name is namespaced, which keeps it apart from VanillaBP's own operations.
 * </p>
 *
 * <p>
 * Between the two moments lies the wait, and the wait is where this blueprint's subject
 * is. The notice says which state it is about
 * ({@code askingForTheStateOfTheEvent}), and the dispatch reads the loan approval as it
 * was then instead of as it is by the time the archive answers. The other kind of entry
 * asks for nothing: everything VanillaBP writes back into the BPMS reads the state of
 * its dispatch, because that is where the case goes on.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ComplianceNotices {

  /** The name of the workflow module, as {@code META-INF/workflow-module} spells it. */
  private static final String WORKFLOW_MODULE_ID = "loan-approval";

  /** The BPMN process id, as the model spells it. */
  private static final String BPMN_PROCESS_ID = "loan_approval";

  /** The operation, namespaced because everything but VanillaBP's own has to be. */
  public static final String OPERATION_NAME = "loan-approval:COMPLIANCE_NOTICE";

  /** The event of the one notice this blueprint sends. */
  public static final String RISK_ASSESSED = "risk-assessed";

  private static final String ARG_EVENT = "event";

  /**
   * One notice per loan approval and event, which is what the key says: the outbox
   * dispatches at least once, so everything going through it has to survive being sent
   * twice, and a key which names the event lets a second event of the same loan approval
   * through.
   */
  private static final PhaseOperation NOTIFY_THE_ARCHIVE = PhaseOperation
      .extensionOperation(OPERATION_NAME)
      .idempotencyKey(
          notice -> Optional.of(
              "%s|%s|%s".formatted(
                  OPERATION_NAME,
                  notice.workflowAggregateId(),
                  notice.args().get(ARG_EVENT))))
      .describedAs(args -> "reporting '%s' to the compliance archive".formatted(args.get(ARG_EVENT)))
      .build();

  @Inject
  PhaseOperationRegistry operations;

  @Inject
  PhaseTwoOutbox outbox;

  @Inject
  AuditedAggregatePersistence loanApprovals;

  @Inject
  ComplianceArchive archive;

  /** Says what to do with a notice once it is due. Every dispatch lands here. */
  @PostConstruct
  public void registerTheOperation() {

    operations.register(NOTIFY_THE_ARCHIVE, (
        notice,
        previouslyAttempted) -> send(notice));

  }

  /**
   * Writes down that the archive has to hear about this loan approval, in the state it is
   * in right now.
   *
   * <p>
   * Runs in the transaction of the decision: if that transaction rolls back, the notice
   * goes with it. The revision is asked for here, while the transaction is open, because
   * afterwards nobody can say any more which state the decision was taken on.
   * </p>
   *
   * @param event        What happened.
   * @param loanApproval The loan approval as the decision left it.
   */
  public void report(
      final String event,
      final Aggregate loanApproval) {

    final var notice = PhaseTwoCall
        .of(
            NOTIFY_THE_ARCHIVE,
            WORKFLOW_MODULE_ID,
            BPMN_PROCESS_ID,
            loanApproval.getLoanRequestId(),
            null,
            Map.of(ARG_EVENT, event))
        .askingForTheStateOfTheEvent(loanApprovals.getAuditingId(loanApproval));

    outbox.schedule(notice);

  }

  /**
   * Sends one notice. VanillaBP calls this after the commit, on a thread and in a
   * transaction of its own, and again later whenever it threw.
   *
   * @param notice The notice as it was written down, including the state it asks for.
   */
  private void send(
      final PhaseTwoCall notice) {

    final var loanRequestId = notice.workflowAggregateId();

    var asItWas = loanApprovals.loadByIdAndAuditingId(loanRequestId, notice.auditingId());

    if (asItWas == null) {
      // The auditing does not have that state any more, so the archive hears about the
      // loan approval as it is today. A report with newer values beats no report, and
      // this line is what tells the two apart later on.
      asItWas = loanApprovals.loadById(loanRequestId);
      log.warn(
          "Loan approval '{}' is not available as it was at revision {}, so the compliance "
              + "archive is told the state of today.",
          loanRequestId,
          notice.auditingId());
    }

    archive.store(notice.args().get(ARG_EVENT), asItWas);

  }

}
