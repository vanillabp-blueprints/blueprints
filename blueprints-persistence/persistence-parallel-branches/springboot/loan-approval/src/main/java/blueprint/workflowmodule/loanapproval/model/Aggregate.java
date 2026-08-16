package blueprint.workflowmodule.loanapproval.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate: one entity per workflow instance, holding everything the process
 * needs to know.
 *
 * <p>
 * What is different here is where the attributes sit. The process splits into two branches
 * which run at the same time, and both of them change the state of this business case. If
 * both wrote attributes of THIS entity, the transaction committing second would write back
 * what it read when it started, and the other branch's result would be gone without an
 * exception or a log line.
 * </p>
 *
 * <p>
 * So this entity carries only what is written before the branches start: the request itself
 * and the credit rating. Everything a branch produces lives in an entity of that branch,
 * {@link PartnerApproval} and {@link DocumentCheck}. Both branches still save this row, and
 * that is harmless as long as none of them changes a value in it.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#two-writers-on-one-aggregate">Two
 *      writers on one aggregate</a>
 */
@Entity
@Table(name = "LOAN_APPROVAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aggregate {

  /**
   * The natural id of the use case. Using a business identifier instead of a generated one
   * makes a workflow started twice for the same business case a detectable duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The amount requested. Written by the API before the workflow starts. */
  @Column
  private Integer amount;

  /** Filled by the service task in front of the split, while one token exists. */
  @Column
  private Integer creditRating;

  /**
   * What the branch waiting for the partner produced. The relation is owned here so that
   * the branch has a row of its own, which is the whole point: two branches, two records.
   */
  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  private PartnerApproval partnerApproval;

  /** What the branch collecting the documents produced. */
  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  private DocumentCheck documentCheck;

  /** Written after both branches joined, so again by a single token. */
  @Column
  private Boolean customerInformed;

}
