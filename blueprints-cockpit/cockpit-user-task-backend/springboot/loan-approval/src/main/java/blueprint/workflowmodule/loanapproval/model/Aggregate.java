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
 * Each user task has two attributes here. One is the id of the open task. A form arrives with
 * that id, and it is how the application finds the loan request the form is about. The other
 * is what the user entered in the form. It is written once, when the form completes the task.
 * Nothing is stored while the user is still typing.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so none of these attributes goes to the
 * BPMS. No sequence flow carries a condition. The Business Cockpit does not read the aggregate
 * either. It gets what the details provider in {@code WorkflowTaskHandler} hands over.
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
   * The natural id of the use case. Using a business identifier instead of a generated one
   * makes a workflow started twice for the same business case a detectable duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** The id of the open user task "Assess risk", null whenever that task is not open. */
  @Column
  private String assessRiskTaskId;

  /** What the user entered in the form "Assess risk", written when the task is completed. */
  @Column
  private String riskAssessment;

  /** The id of the open user task "Check documents", null whenever that task is not open. */
  @Column
  private String checkDocumentsTaskId;

  /** What the user entered in the form "Check documents", written when the task is completed. */
  @Column
  private String documentsNote;

}
