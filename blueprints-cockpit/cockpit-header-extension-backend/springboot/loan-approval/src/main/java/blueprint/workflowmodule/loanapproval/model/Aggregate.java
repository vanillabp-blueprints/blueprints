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
 * The workflow aggregate: one entity per workflow instance, holding everything the process
 * needs to know. There are no process variables. This is the single source of truth, and it
 * stays a normal JPA entity your application can use like any other.
 *
 * <p>
 * Besides what the user typed into the start form, it holds the id of the open user task, so
 * the module knows which task to complete.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so none of these attributes goes to the
 * BPMS. The Business Cockpit does not read the aggregate either. It gets what the cockpit
 * adapter reports.
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
   * The natural id of the use case. The start form gets it as the answer, and the cockpit shows
   * it as the business id of the case.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** Who asks for the loan. */
  @Column
  private String applicant;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** The id of the open user task "Review loan request", null whenever that task is not open. */
  @Column
  private String reviewTaskId;

}
