package blueprint.workflowmodule.loanapproval.model;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate: one instance per workflow, holding everything the process needs to
 * know. There are no process variables - this is the single source of truth.
 *
 * <p>
 * Look at what is not here. No entity annotation, no column, no collection, nothing naming a
 * database. VanillaBP does not require any of it: the aggregate is a Java class, and how it is
 * stored is answered by {@code AggregateStore} alone. That is the whole point of this blueprint,
 * and the reason it can be read as the recipe for a persistence VanillaBP has never heard of.
 * </p>
 *
 * <p>
 * Nothing of this class reaches the BPMS. It is annotated {@code @NoSyncWithBPMS}, and no
 * attribute takes that back: the model has one service task and no expression which reads the
 * aggregate, so there is nothing to hand over. An attribute a model starts to read gets
 * {@code @SyncWithBPMS} on that day and not before.
 * </p>
 *
 * <p>
 * The loan request id travels anyway. A BPMS without a business key of its own is given the
 * aggregate's ID, because that is how VanillaBP finds the workflow again, and here
 * {@code AggregateStore} names the attribute it is read from.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The natural id of the use case. Using a business identifier instead of a generated one
   * makes a workflow started twice for the same business case a detectable duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  private String loanRequestId;

  /** The amount requested. */
  private Integer amount;

  /** Filled by the business code the service task of the process triggers. */
  private Integer creditRating;

}
