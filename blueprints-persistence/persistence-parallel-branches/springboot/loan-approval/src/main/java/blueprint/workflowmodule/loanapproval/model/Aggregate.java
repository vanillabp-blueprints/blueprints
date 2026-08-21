package blueprint.workflowmodule.loanapproval.model;

import org.hibernate.annotations.DynamicUpdate;

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
 * {@link PartnerApproval} and {@link DocumentCheck}.
 * </p>
 *
 * <p>
 * That alone was not enough, and the reason is worth knowing before copying this: the
 * ASSOCIATION lives here. Two {@code @OneToOne} attributes mean two foreign-key columns on
 * this row, so a branch which creates its own entity still writes this row to point at it.
 * Without {@code @DynamicUpdate} that write covers every column, the transaction committing
 * second puts back the foreign key it read at its start, and one branch's result is orphaned:
 * its row exists and nothing refers to it any more. Measured on Camunda 8, where the two
 * branches really are delivered at the same time; an embedded engine serializes the jobs of
 * one instance and hides it.
 * </p>
 *
 * <p>
 * {@code @DynamicUpdate} makes Hibernate write only the columns a branch changed, which is
 * enough here because each branch touches its own. The other way out would be to let each
 * branch's entity own the foreign key ({@code @OneToOne(mappedBy = ...)} here), so that a
 * branch does not write this row at all. That is the cleaner model and it costs a change in
 * both entities and in the business code.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#two-writers-on-one-aggregate">Two
 *      writers on one aggregate</a>
 */
@Entity
@DynamicUpdate
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
