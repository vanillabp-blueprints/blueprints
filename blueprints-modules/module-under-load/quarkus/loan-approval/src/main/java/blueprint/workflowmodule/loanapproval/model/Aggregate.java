package blueprint.workflowmodule.loanapproval.model;

import java.time.Instant;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import io.vanillabp.spi.service.SyncWithBPMS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The workflow aggregate: one entity per workflow instance, holding everything the
 * process needs to know. There are no process variables - this is the single source of
 * truth, and it stays a normal JPA entity your application can use like any other.
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, and two values say {@code @SyncWithBPMS}:
 * the decision the gateway reads, and the due date of the payout run the timer reads. Nothing
 * else travels to the BPMS. The documents of a loan request are the biggest part of the
 * aggregate, and they never leave your database.
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

  /**
   * What the customer handed in with the request. Its size is what the load profile calls
   * the size of the aggregate, because every task reads and writes this row.
   */
  @Lob
  @Column
  @ToString.Exclude
  private String documents;

  /** The answer of the credit bureau. It stays empty if the bureau did not answer in time. */
  @Column
  private Integer creditScore;

  /** The id of the open assessment, while a clerk may work on it. */
  @Column
  private String assessmentTaskId;

  /** The decision of the clerk. It stays empty until the clerk has decided. */
  @Column
  private Boolean approval;

  /**
   * When the next payout run starts. Every loan approved before that moment gets the same
   * value, so the timer which reads it wakes all of them at once. It is a point in time, so
   * {@code loan-approval.yaml} declares it: the timer reads it, and nothing else does.
   */
  @Column
  @SyncWithBPMS
  private Instant payoutRunAt;

  /** Set by the payout. */
  @Column
  private Boolean paidOut;

  /** Set when the customer was told about the rejection. */
  @Column
  private Boolean rejectionSent;

  /**
   * What the gateway behind the user task asks. A plain boolean, because a decision nobody
   * made yet must not reach a gateway as an empty value.
   *
   * @return Whether the clerk approved the loan.
   */
  @SyncWithBPMS
  public boolean isApproved() {

    return Boolean.TRUE.equals(approval);

  }

}
