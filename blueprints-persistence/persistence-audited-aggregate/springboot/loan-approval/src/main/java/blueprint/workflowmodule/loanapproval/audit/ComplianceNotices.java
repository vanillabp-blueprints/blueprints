package blueprint.workflowmodule.loanapproval.audit;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.ComplianceArchive;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.integration.spi.PhaseOperation;
import io.vanillabp.integration.spi.PhaseOperationRegistry;
import io.vanillabp.integration.spi.PhaseTwoCall;
import io.vanillabp.integration.spi.PhaseTwoOutbox;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Reporting a decision to the compliance archive: written down when the decision is
 * taken, sent once that transaction committed.
 *
 * <p>
 * Between the two moments lies the wait, and the wait is what this blueprint is about.
 * The report carries the id of the change it belongs to, so what the archive is handed is
 * the state of the decision and not the state of the day the archive happened to be
 * reachable. The id comes from
 * {@link AuditedAggregatePersistence#idOfTheChangeBeingMade()}, and the report is served
 * with it again when it is sent.
 * </p>
 *
 * <p>
 * How the report survives the commit is not the subject here: it rides VanillaBP's
 * outbox, which the application already has, so a report cannot get lost and cannot be
 * sent for a decision which was rolled back. An application with a mechanism of its own
 * uses that one and changes nothing about the rest of this class.
 * </p>
 */
@Slf4j
@Component
public class ComplianceNotices {

  /** The name of the workflow module, as {@code META-INF/workflow-module} spells it. */
  private static final String WORKFLOW_MODULE_ID = "loan-approval";

  /** The BPMN process id, as the model spells it. */
  private static final String BPMN_PROCESS_ID = "loan_approval";

  /** How the report is named where it is stored. Namespaced, as anything but VanillaBP's own. */
  public static final String OPERATION_NAME = "loan-approval:COMPLIANCE_NOTICE";

  /** The event of the one notice this blueprint sends. */
  public static final String RISK_ASSESSED = "risk-assessed";

  private static final String ARG_EVENT = "event";

  /**
   * The state the report means, named by the id of the change which produced it. It is an
   * argument of the report like the event is: VanillaBP carries it and reads nothing in
   * it.
   */
  private static final String ARG_CHANGE = "change";

  /**
   * One report per loan approval and event, which is what the key says: a report may be
   * sent twice after a crash, and a key which names the event lets a second event of the
   * same loan approval through.
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

  @Autowired
  private PhaseOperationRegistry operations;

  @Autowired
  private PhaseTwoOutbox outbox;

  @Autowired
  private AuditedAggregatePersistence loanApprovals;

  @Autowired
  private ComplianceArchive archive;

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
   * goes with it. The id of the change is asked for here, while the transaction is open,
   * because afterwards nobody can say any more which state the decision was taken on.
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
            Map.of(
                ARG_EVENT, event,
                ARG_CHANGE, loanApprovals.idOfTheChangeBeingMade()));

    outbox.schedule(notice);

  }

  /**
   * Sends one notice. VanillaBP calls this after the commit, on a thread and in a
   * transaction of its own, and again later whenever it threw.
   *
   * @param notice The notice as it was written down, including the change it is about.
   */
  private void send(
      final PhaseTwoCall notice) {

    final var loanRequestId = notice.workflowAggregateId();

    final var change = notice.args().get(ARG_CHANGE);

    var asItWas = loanApprovals.loadByIdAsOfChange(loanRequestId, change);

    if (asItWas == null) {
      // The auditing does not have that state any more, so the archive hears about the
      // loan approval as it is today. A report with newer values beats no report, and
      // this line is what tells the two apart later on.
      asItWas = loanApprovals.loadById(loanRequestId);
      log.warn(
          "Loan approval '{}' is not available as it was at change {}, so the compliance "
              + "archive is told the state of today.",
          loanRequestId,
          change);
    }

    archive.store(notice.args().get(ARG_EVENT), asItWas);

  }

}
