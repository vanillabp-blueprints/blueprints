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
 * <strong>One aggregate, two processes.</strong> The called process of this blueprint works
 * on this very entity, because a call activity used for decomposition is a section of the
 * same business case: whatever it does could as well have been drawn into the calling
 * model. There is no second entity, no copy and nothing to keep in sync - the attributes
 * below say which step of which process wrote them.
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
public class Aggregate {

  /**
   * The natural id of the use case. Using a business identifier instead of a generated
   * one makes a workflow started twice for the same business case a detectable
   * duplicate.
   *
   * <p>
   * It is also the handle the BPMS keeps on the workflow, which is why the call activity
   * has to pass it on to the called process - a process instance whose aggregate cannot be
   * found is a workflow VanillaBP has to refuse to serve.
   * </p>
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** Filled by the business code the first service task of the calling process triggers. */
  @Column
  private Integer creditRating;

  /** Written by the called process: what the customer can put up as a security. */
  @Column
  private Integer collateralValue;

  /** Written by the called process: the share of the income already spent on debt. */
  @Column
  private Integer debtRatio;

  /**
   * Written by the calling process after the call activity returned, from what the called
   * process left on this aggregate.
   */
  @Column
  private String decision;

}
