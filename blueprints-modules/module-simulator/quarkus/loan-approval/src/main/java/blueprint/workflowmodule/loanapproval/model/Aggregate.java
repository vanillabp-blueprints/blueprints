package blueprint.workflowmodule.loanapproval.model;

import java.math.BigDecimal;

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
 * The workflow aggregate: one entity per workflow instance, holding everything the process needs
 * to know. There are no process variables. This is the single source of truth, and it stays a
 * normal JPA entity your application can use like any other.
 *
 * <p>
 * It is also what the integration test waits for. Each answer of a system around the module
 * leaves a trace here: the credit score, the contract sent for signing, the signature. So the
 * test asks the application how far it came, and never the BPMS or the simulator.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so no attribute of it reaches the BPMS. No
 * expression in the model reads the aggregate.
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
   * The natural id of the use case. The contract handed to the e-signature service carries the
   * same id, so the callback finds its way back without a table which maps one id to the other.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The customer asking for the loan. */
  @Column
  private String customerId;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** The answer of the credit rating, asked before the workflow started. */
  @Column
  private Integer creditScore;

  /**
   * The id of the open user task "Prepare contract". It is the handle needed to complete the
   * task, and null whenever the task is not open.
   */
  @Column
  private String prepareContractTaskId;

  /** Written when the contract was prepared. */
  @Column
  private BigDecimal interestRate;

  /** Set when the e-signature service took the contract. */
  @Column
  private Boolean contractSentForSigning;

  /** Set when the e-signature service reported the signature. */
  @Column
  private Boolean contractSigned;

}
