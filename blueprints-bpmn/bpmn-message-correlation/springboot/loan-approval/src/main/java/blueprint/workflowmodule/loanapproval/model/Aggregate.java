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
 * A message is where this matters most: its payload stops at the API. What the process
 * and everything behind it may read is what the application wrote here.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS} and no attribute of it is annotated
 * {@code @SyncWithBPMS}, so the BPMS holds none of these values. The correlation still
 * finds the workflow, because it reads the workflow aggregate's ID, and that one travels
 * to every BPMS no matter what the annotations say: it is how VanillaBP gets from a
 * process instance back to the workflow. A model which names a correlation key of its own
 * is the other case - such a key is read by the BPMS, so the attribute behind it has to
 * carry {@code @SyncWithBPMS}.
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
   * <p>
   * It is also the value the annotations cannot keep out, and the one this blueprint
   * depends on: correlating reads it, so a BPMS without a business key of its own is
   * given it as a process variable.
   * </p>
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
   * Who signed the contract. It arrives with the message and is written here BEFORE the
   * message is correlated: the content of a message never travels to the BPMS, so
   * whatever the process may need afterwards has to be on the aggregate first.
   */
  @Column
  private String contractSignedBy;

  /** Written by the service task behind the message event. */
  @Column
  private Boolean paidOut;

}
