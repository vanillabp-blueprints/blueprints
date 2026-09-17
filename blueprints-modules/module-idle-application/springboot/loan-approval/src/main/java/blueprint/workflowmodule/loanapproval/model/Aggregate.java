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
 * The class is annotated {@code @NoSyncWithBPMS}, so none of the attributes below is
 * shared with the BPMS. No expression in the model reads the aggregate: the withdrawal
 * period is a fixed duration and there is no condition on any sequence flow. What the BPMS
 * gets is the aggregate's ID, which VanillaBP always shares because it is how it finds the
 * workflow again. An attribute a model does read is named with {@code @SyncWithBPMS}, one
 * by one.
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

  /** Filled by the business code the service task of the process triggers. */
  @Column
  private Integer creditRating;

  /**
   * Set by the task behind the timer. It is what tells a reader that the application woke
   * up again: nothing ran while the workflow waited, and then this was written.
   */
  @Column
  private Boolean paidOut;

}
