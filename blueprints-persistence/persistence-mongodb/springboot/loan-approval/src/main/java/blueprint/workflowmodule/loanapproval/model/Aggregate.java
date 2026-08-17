package blueprint.workflowmodule.loanapproval.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate: one document per workflow instance, holding everything the process
 * needs to know. There are no process variables - this is the single source of truth, and it
 * stays a normal document your application can use like any other.
 *
 * <p>
 * Compared to the base blueprint only the mapping changed: a collection instead of a table,
 * and no column declarations, because a document database asks for none. What VanillaBP does
 * with the aggregate is the same, and so is every other class of this workflow module.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Document(collection = "LOAN_APPROVAL")
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

  /** The amount requested. */
  private Integer amount;

  /** Filled by the business code the service task of the process triggers. */
  private Integer creditRating;

}
