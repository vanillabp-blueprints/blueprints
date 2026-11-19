package blueprint.workflowmodule.loanapproval.model;

import org.hibernate.annotations.DynamicUpdate;

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
 * The workflow aggregate: one entity per workflow instance, holding everything the process needs
 * to know. There are no process variables. This is the single source of truth, and it stays a
 * normal JPA entity your application can use like any other.
 *
 * <p>
 * It is also what the process driver waits for. Each step of the process leaves a trace here:
 * the id of an open user task, the contract sent for signing, the signature or the decline. So
 * the driver asks the application how far a loan approval came, and never the BPMS.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so no attribute of it reaches the BPMS unless
 * it says so. Only {@link #riskAssessmentNeeded} does, because the gateway of the model reads it.
 * </p>
 *
 * <p>
 * It is {@code @DynamicUpdate}, so a transaction writes only the columns it changed. The
 * e-signature service may report the signature while the workflow still writes the aggregate in
 * a transaction of its own, for example right after it sent the contract. Without it, the
 * transaction which commits last would write every column, and an older value would overwrite the
 * signature, or the other way round.
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
@DynamicUpdate
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The natural id of the use case. The contract handed to the e-signature service carries the
   * same id, so the callback finds its way back without a table which maps one id to the other.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/The-workflow-aggregate#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The customer asking for the loan. */
  @Column
  private String customerId;

  /** The name of the customer, who signs the contract. */
  @Column
  private String customerName;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** The answer of the credit rating, asked before the workflow started. */
  @Column
  private Integer creditScore;

  /**
   * Whether somebody has to assess the risk before the contract goes out. The business code
   * answers it when the loan is requested, from the amount and the limit the module is
   * configured with. The gateway of the model reads it, so it is the one attribute shared with
   * the BPMS. It is a plain {@code boolean}, because a decision must never rest on a null.
   */
  @Column
  @SyncWithBPMS
  private boolean riskAssessmentNeeded;

  /**
   * The id of the open user task "Assess risk". It is the handle needed to complete the task,
   * and null whenever the task is not open.
   */
  @Column
  private String assessRiskTaskId;

  /** Set when somebody assessed the risk. */
  @Column
  private Boolean riskAssessed;

  /** Set when the e-signature service took the contract. */
  @Column
  private Boolean contractSentForSigning;

  /**
   * The id of the open user task "Check contract", null whenever the task is not open.
   */
  @Column
  private String checkContractTaskId;

  /**
   * Set when the e-signature service reported the signature. It is not a message to the
   * process. The task "Check contract" waits for it, and the business code refuses to complete
   * the task while it is missing.
   */
  @Column
  private Boolean contractSigned;

  /** Set when somebody checked the signed contract, which completes the workflow. */
  @Column
  private Boolean contractChecked;

  /**
   * Set when the e-signature service reported that the customer declined to sign. Unlike the
   * signature, this is also a message to the process, which takes the task "Check contract" away.
   */
  @Column
  private Boolean contractDeclined;

  /**
   * Set when the workflow took the task "Check contract" away because of the decline, which ends
   * the workflow. It is written after the engine moved on, so the process driver waits for it.
   */
  @Column
  private Boolean loanDeclined;

}
