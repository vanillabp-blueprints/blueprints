package blueprint.workflowmodule.ratewatch;

import java.time.LocalDateTime;
import java.util.Optional;

import blueprint.workflowmodule.ratewatch.model.Aggregate;
import blueprint.workflowmodule.ratewatch.model.AggregateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the rate watch: somebody asks to be told when the next rate is
 * published, and the process waits for exactly that.
 *
 * <p>
 * It is a use case of its own, with its own aggregate and its own process, and it shares
 * nothing with the loan approval but the workflow module they live in. That is enough: a
 * signal is broadcast per workflow module, so the loan approval's broadcast reaches this
 * process too, and neither side knows about the other.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service {

  @Inject
  AggregateRepository rateWatches;

  @Inject
  Workflow workflow;

  /**
   * Starts watching for the next published rate.
   *
   * @param watchId The natural id of this watch.
   */
  @Transactional
  public void startWatching(
      final String watchId) {

    final var rateWatch = Aggregate
        .builder()
        .watchId(watchId)
        .build();

    workflow.watchRequested(rateWatch);

    log.info(
        "Rate watch '{}' started. It waits for the same signal the loan approvals wait for,"
            + " and nobody sends it twice",
        watchId);

  }

  /**
   * The signal arrived, which is all this process was waiting for.
   *
   * @param rateWatch The workflow's aggregate.
   */
  public void rateNoticed(
      final Aggregate rateWatch) {

    rateWatch.setNoticedAt(LocalDateTime.now());

    log.info(
        "Rate watch '{}' noticed the publication, although the broadcast was sent by the loan"
            + " approval use case",
        rateWatch.getWatchId());

  }

  /**
   * The state of a rate watch.
   *
   * @param watchId The natural id of the watch.
   * @return The rate watch, if it exists.
   */
  public Optional<Aggregate> getRateWatch(
      final String watchId) {

    return rateWatches.findByIdOptional(watchId);

  }

}
