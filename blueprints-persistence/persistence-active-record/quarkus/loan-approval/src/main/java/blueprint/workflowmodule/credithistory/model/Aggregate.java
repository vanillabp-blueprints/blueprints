package blueprint.workflowmodule.credithistory.model;

import java.util.Optional;

import org.bson.codecs.pojo.annotations.BsonId;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate of the credit history, the second use case of this workflow module,
 * and the reason the module exists twice over: this one is an active record as well, but in
 * MongoDB.
 *
 * <p>
 * Extending {@link PanacheMongoEntityBase} adds {@code persist}, {@code delete} and the static
 * finders to the document, exactly as {@code PanacheEntityBase} does for the JPA entity of the
 * loan approval. Neither aggregate has a repository, and the two are stored in different
 * databases - which is the point of having both in one workflow module: VanillaBP resolves the
 * idiom per aggregate, so an application may mix them. A repository would win over the aggregate
 * being an active record, and Spring Data answers last, and that order is decided per aggregate
 * rather than once for the application.
 * </p>
 *
 * <p>
 * The build log says so, one line per aggregate:
 * </p>
 *
 * <pre>
 * Using VanillaBP's MongoDB Panache active record persistence for workflow aggregate
 * 'blueprint.workflowmodule.credithistory.model.Aggregate'
 * Using VanillaBP's Hibernate ORM Panache active record persistence for workflow aggregate
 * 'blueprint.workflowmodule.loanapproval.model.Aggregate'
 * </pre>
 *
 * <p>
 * A MongoDB aggregate is written inside a MongoDB transaction, because Panache enlists its
 * session in the transaction VanillaBP runs a task or a phase two in. That needs a replica set;
 * the dev services start one, and a production deployment has to be one as well. VanillaBP
 * probes for it while starting and warns if the server it talks to is a standalone.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#persisting-workflow-aggregates">Persisting
 *      workflow aggregates</a>
 */
@MongoEntity(collection = "CREDIT_HISTORY")
@Data
// The superclass has no state of its own, it only adds the operations. Saying so explicitly
// is what keeps the build free of Lombok's question about it.
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aggregate extends PanacheMongoEntityBase {

  /**
   * The natural id of the use case. Using a business identifier instead of a generated one
   * makes a workflow started twice for the same business case a detectable duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @BsonId
  private String creditHistoryId;

  /** How many years back the customer asked for. */
  private Integer years;

  /** Filled by the business code the service task of the process triggers. */
  private Integer entriesFound;

  /**
   * Loads a credit history by its natural id.
   *
   * <p>
   * Written out rather than left to the inherited {@code findByIdOptional} for the same reason
   * as in the loan approval: the inherited method is generic and hands the caller a
   * {@code PanacheMongoEntityBase}. One method per question, named after the question.
   * </p>
   *
   * <p>
   * A caller needs a transaction or an active request context, because there is no class in
   * between which could have brought one along.
   * </p>
   *
   * @param creditHistoryId The natural id of the credit history.
   * @return The credit history, if it exists.
   */
  public static Optional<Aggregate> byId(
      final String creditHistoryId) {

    return findByIdOptional(creditHistoryId);

  }

}
