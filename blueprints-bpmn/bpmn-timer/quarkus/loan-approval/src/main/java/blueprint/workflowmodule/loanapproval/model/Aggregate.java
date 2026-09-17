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
 * The attribute this blueprint is about is {@link #partnerApprovalTaskId}. A task waiting
 * for a surrounding system may wait for days, so something has to remember which task the
 * answer belongs to. The aggregate is where that belongs: it is transactional, it is
 * queryable, and it survives a restart of both sides.
 * </p>
 *
 * <p>
 * The class carries {@code @NoSyncWithBPMS}, so none of these attributes is shared with
 * the BPMS. No expression in the model reads the aggregate: both timers carry a literal
 * duration, {@code PT1S} and {@code PT3S}. That is why no attribute carries
 * {@code @SyncWithBPMS} here. The BPMS holds the aggregate's ID and nothing else, because
 * that is how VanillaBP finds the workflow again. A deadline read from an expression would
 * be the other case: then the attribute behind it has to be shared.
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
   * The id of the task waiting for the partner's answer, reported by the BPMS when the
   * task was delivered. It is the handle needed to complete that task, and it is null
   * whenever nothing is waiting - which the deadline is what makes true again.
   */
  @Column
  private String partnerApprovalTaskId;

  /** Whether the partner answered, written when their answer arrives. */
  @Column
  private Boolean partnerApproved;

  /** Written by the service task following the completed task. */
  @Column
  private Boolean customerInformed;

  /** Written by the service task the timer boundary event leads to. */
  @Column
  private Boolean timedOut;

}
