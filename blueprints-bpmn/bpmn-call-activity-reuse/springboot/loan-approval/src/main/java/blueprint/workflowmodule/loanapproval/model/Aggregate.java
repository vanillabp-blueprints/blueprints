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
 * The workflow aggregate of the loan approval. There are no process variables. This entity is
 * the single source of truth, and it stays a normal JPA entity your application can use like
 * any other.
 *
 * <p>
 * The credit check has an aggregate of its own. This one only keeps the id of the credit check
 * it asked for, and the score which came back. So the loan approval never reads the data of
 * the credit check, and the credit check never reads this one.
 * </p>
 *
 * <p>
 * The entity is given a name, like the other two aggregates of this module. Three JPA entities
 * called {@code Aggregate} in one persistence unit would clash otherwise.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so the BPMS holds none of these values. The
 * correlation still finds the workflow, because it reads the workflow aggregate's ID, and
 * VanillaBP always shares that one.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity(name = "LoanApproval")
@Table(name = "LOAN_APPROVAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The natural id of the use case.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/The-workflow-aggregate#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** The credit check this loan approval asked for. Written by the first service task. */
  @Column
  private String creditCheckId;

  /**
   * The score the credit check reported. It is written here BEFORE the message is
   * correlated, because the content of a message never travels to the BPMS.
   */
  @Column
  private Integer creditScore;

  /** The decision, written by the service task behind the message event. */
  @Column
  private Boolean approved;

}
