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
 * The workflow aggregate: one entity per workflow instance, holding everything the
 * process needs to know. There are no process variables - this is the single source of
 * truth, and it stays a normal JPA entity your application can use like any other.
 *
 * <p>
 * A signal shows the limit of what may travel: nothing at all. It is not addressed to a
 * workflow, so there is no aggregate the sender could write to beforehand. What a waiting
 * workflow needs is read from the application's data after the signal arrived - here from
 * {@link InterestRate} - and lands here.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so none of these attributes is written to
 * the BPMS. Nothing in the two models of this workflow module reads the aggregate: the
 * signal name is a fixed string, and no condition or other expression looks at the data.
 * So the BPMS holds the workflow aggregate's ID and nothing else, which is what VanillaBP
 * needs to find the workflow again.
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

  /** Filled by the business code the service task of the process triggers. */
  @Column
  private Integer creditRating;

  /**
   * The rate this loan is offered at, written by the service task behind the signal event.
   * A value here means the workflow passed the catch event, which is how the test tells a
   * waiting workflow from a continued one.
   *
   * <p>
   * A {@code Double}, although the published rate is a {@code BigDecimal} in
   * {@link InterestRate}. Nothing of this aggregate is shared, so the type is the
   * application's own choice. It would matter the day the process had to decide on the
   * rate: the attribute would get {@code @SyncWithBPMS}, and an engine has variable types
   * for a handful of Java types only. A double is among them and stays a number in the
   * engine's tooling and in a BPMN expression. A decimal is not, and the engine stores it
   * as a serialized object nobody can read until the application configures a
   * serialization format. Percent needs no exact scale, so
   * {@code Service#applyInterestRate} converts.
   * Where the scale does matter, keep the value in the application's own data and let the
   * aggregate share what the process has to decide on.
   * </p>
   */
  @Column
  private Double interestRate;

}
