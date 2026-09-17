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
 * <p>
 * Every column is named explicitly, which is what an application owning its schema does: the
 * migration and the entity have to agree, and a naming strategy deciding it means the names
 * depend on a default rather than on something written down.
 * <p>
 * Nothing here reaches the BPMS. That is what {@code @NoSyncWithBPMS} on the class says:
 * the model of this blueprint has a single service task and no condition, so no expression
 * reads the aggregate, and the engine gets along without these values. What it does hold is
 * the aggregate's ID, because that is how VanillaBP finds the workflow again. If a
 * condition is added to the model later, the getter it reads gets {@code @SyncWithBPMS},
 * and nothing else does.
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
  @Column(name = "LOAN_REQUEST_ID")
  private String loanRequestId;

  /** The amount requested. */
  @Column(name = "AMOUNT")
  private Integer amount;

  /** Filled by the business code the service task of the process triggers. */
  @Column(name = "CREDIT_RATING")
  private Integer creditRating;

}
