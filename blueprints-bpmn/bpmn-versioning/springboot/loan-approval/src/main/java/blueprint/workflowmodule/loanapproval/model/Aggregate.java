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
 * Nothing of this class reaches the BPMS. It is annotated {@code @NoSyncWithBPMS}, and no
 * attribute takes that back, because no expression in the model reads the aggregate. The
 * model has one service task and carries no condition on a sequence flow and no timer. The
 * expression it does carry names the task definition, which is a handler name.
 * </p>
 *
 * <p>
 * The version a workflow runs on is not an attribute either. VanillaBP asks the BPMS which
 * version the workflow was started on and picks the method whose range covers it, so
 * neither {@link #assessedBy} nor {@link #riskScore} has to travel although the two
 * versions write different ones. The blueprint ships one model, and it is this same model
 * which deploys as version 1 into an empty engine, so no older version reads anything
 * either.
 * </p>
 *
 * <p>
 * The loan request id travels anyway. A BPMS without a business key of its own is given
 * the aggregate's ID, because that is how VanillaBP finds the workflow again.
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
   * Who assessed the risk, filled by workflows running on version 1 of the process. It stays
   * in the aggregate although no new workflow writes it: the workflows which do are still
   * running, and their data has to survive the deployment which changed the model.
   */
  @Column
  private String assessedBy;

  /** The score every version after the first one computes instead. */
  @Column
  private Integer riskScore;

}
