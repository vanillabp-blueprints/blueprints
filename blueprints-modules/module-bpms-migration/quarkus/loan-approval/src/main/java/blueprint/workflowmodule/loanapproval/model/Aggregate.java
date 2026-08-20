package blueprint.workflowmodule.loanapproval.model;

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
 * In this blueprint it is also what survives a restart of the application with a different
 * BPMS configuration. The aggregate is where the business case lives; which BPMS runs the
 * workflow of that case is configuration, and nothing here mentions it.
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
   * The id of the open risk assessment, kept because answering a user task needs it. It
   * is the BPMS-side id of the task, so it belongs to the BPMS holding this workflow -
   * which is exactly what VanillaBP looks up when the answer arrives.
   */
  @Column
  private String riskAssessmentTaskId;

  /** What the risk assessment concluded, written when the user task is answered. */
  @Column
  private Boolean riskAcceptable;

  /**
   * Who signed the contract. It arrives with the message and is written here BEFORE the
   * message is correlated: the content of a message never travels to the BPMS, so
   * whatever the process may need afterwards has to be on the aggregate first.
   */
  @Column
  private String contractSignedBy;

  /** Written by the service task behind the message event, the last step of the process. */
  @Column
  private Boolean paidOut;

}
