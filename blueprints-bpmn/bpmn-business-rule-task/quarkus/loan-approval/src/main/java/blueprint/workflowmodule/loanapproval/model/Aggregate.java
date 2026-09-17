package blueprint.workflowmodule.loanapproval.model;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import io.vanillabp.spi.service.SyncWithBPMS;
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
 * A decision table reads what the BPMS holds, so what its inputs name has to be there.
 * The class is annotated {@code @NoSyncWithBPMS}, which shares nothing, and the two
 * attributes the inputs of {@code loan_approval.dmn} name carry {@code @SyncWithBPMS}.
 * The BPMS therefore holds {@link #amount}, {@link #creditRating} and the aggregate's ID,
 * which VanillaBP always shares because it is how it finds the workflow again.
 * </p>
 *
 * <p>
 * What the table decided is not part of that. The engine writes its result as a variable
 * of the workflow, and {@link #approval} is only the copy the application keeps.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#fine-grained-control-over-attributes-synchronized-to-the-bpms">Sharing
 *      workflow-aggregate data</a>
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

  /**
   * The amount requested. Shared with the BPMS because the first input of the decision
   * table reads it.
   */
  @SyncWithBPMS
  @Column
  private Integer amount;

  /**
   * Filled by the business code the service task of the process triggers. Shared with the
   * BPMS because the second input of the decision table reads it.
   */
  @SyncWithBPMS
  @Column
  private Integer creditRating;

  /**
   * What the decision table decided, written by the task behind the business rule task.
   * The process itself needs no such attribute: the gateway reads the decision's result
   * straight from the workflow. This is here because the APPLICATION wants to keep it,
   * which is why it is not shared back.
   */
  @Column
  private String approval;

}
