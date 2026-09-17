package blueprint.workflowmodule.loanapproval.model;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate: one entity per workflow instance, holding everything the
 * process needs to know. There are no process variables - this is the single source of
 * truth, and it stays a normal JPA entity your application can use like any other.
 *
 * <p>
 * Nothing here serves the viewer. What a diagram and its colouring need comes from the
 * BPMS through {@code ProcessService}, and the only thing the application contributes is
 * this aggregate, which is how VanillaBP finds the workflow again.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so no attribute goes to the BPMS. The
 * model has no reason to get one: it runs from the start event to the end over two
 * service tasks and one wait state, and no expression in it reads the aggregate. What
 * the BPMS holds is the aggregate's ID, which VanillaBP always shares because that is
 * how it finds the workflow again. An attribute gets {@code @SyncWithBPMS} the day a
 * model starts reading it.
 * </p>
 *
 * <p>
 * The history and the diagram are unaffected by this. They come from the BPMS' own
 * record of what ran, not from data the application shared with it.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity
@Table(name = "LOAN_APPROVAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The natural id of the use case. Using a business identifier instead of a generated
   * one makes a workflow started twice for the same business case a detectable
   * duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** Filled by the business code the first service task of the process triggers. */
  @Column
  private Integer creditRating;

  /**
   * The id of the task the workflow waits at, taken from {@code @TaskId}. It exists so
   * the process can be moved on from the API - the workflow has to stand somewhere for a
   * history to be worth looking at.
   */
  @Column
  private String partnerApprovalTaskId;

  /** Written after the partner approved, by the task following the wait state. */
  @Column
  private String decision;

}
