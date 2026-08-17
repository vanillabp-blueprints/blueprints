package blueprint.workflowmodule.loanapproval.model;

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
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
