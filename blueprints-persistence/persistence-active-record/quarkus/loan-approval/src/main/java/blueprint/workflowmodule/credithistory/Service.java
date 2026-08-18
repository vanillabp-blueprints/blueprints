package blueprint.workflowmodule.credithistory;

import java.util.Optional;

import blueprint.workflowmodule.credithistory.model.Aggregate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the credit history: a customer asks for the entries of the last
 * years, and the process collects them.
 *
 * <p>
 * It is a use case of its own, with its own aggregate and its own process, and it shares
 * nothing with the loan approval but the workflow module they live in. Where they differ is
 * the one thing this blueprint is about: this aggregate is stored in MongoDB, the loan
 * approval's in a relational database, and neither says a word about it.
 * </p>
 *
 * <p>
 * The transactions sit where they sit in the loan approval, and for the same reason: on the
 * methods the API calls, because starting a workflow needs one and reading an active record
 * needs one too, and never on the methods a task handler calls, because VanillaBP owns that
 * transaction.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service {

  /** Credit histories are reported quarterly, so a year contributes four entries. */
  private static final int ENTRIES_PER_YEAR = 4;

  @Inject
  Workflow workflow;

  /**
   * A customer requests their credit history.
   *
   * @param creditHistoryId The natural id of the request.
   * @param years           How many years back the customer asked for.
   */
  @Transactional
  public void requestCreditHistory(
      final String creditHistoryId,
      final int years) {

    final var creditHistory = Aggregate
        .builder()
        .creditHistoryId(creditHistoryId)
        .years(years)
        .build();

    workflow.creditHistoryRequested(creditHistory);

    log.info("Credit history '{}' started", creditHistoryId);

  }

  /**
   * Collects the entries of a credit history. A real application would ask a credit bureau
   * here; what matters for the blueprint is that this code sits in the business service and
   * writes to the aggregate, no matter which database that aggregate lives in.
   *
   * @param creditHistory The credit history to collect the entries for.
   */
  public void collectHistoryEntries(
      final Aggregate creditHistory) {

    final var entries = creditHistory.getYears() * ENTRIES_PER_YEAR;

    creditHistory.setEntriesFound(entries);

    log.info(
        "Credit history '{}' has {} entries",
        creditHistory.getCreditHistoryId(),
        entries);

  }

  /**
   * The state of a credit history, as far as the process has come.
   *
   * @param creditHistoryId The natural id of the request.
   * @return The credit history, if it exists.
   */
  @Transactional
  public Optional<Aggregate> getCreditHistory(
      final String creditHistoryId) {

    return Aggregate.byId(creditHistoryId);

  }

}
