package blueprint.workflowmodule.loanapproval.model;

import org.bson.codecs.pojo.annotations.BsonId;

import io.quarkus.mongodb.panache.common.MongoEntity;
import io.vanillabp.spi.service.NoSyncWithBPMS;
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
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}: the aggregate belongs to the application,
 * and the BPMS is given only what a model reads. This model reads nothing of it. There is no
 * condition on a sequence flow, no timer and no multi-instance collection, so no attribute
 * carries {@code @SyncWithBPMS} and the BPMS holds the aggregate's ID alone, which VanillaBP
 * always shares because it is how it finds the workflow again. The day an expression in the
 * model starts reading an attribute, that attribute gets {@code @SyncWithBPMS} and nothing
 * else does.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@MongoEntity(collection = "LOAN_APPROVAL")
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
  @BsonId
  private String loanRequestId;

  /** The amount requested. */
  private Integer amount;

  /** Filled by the business code the service task of the process triggers. */
  private Integer creditRating;

}
