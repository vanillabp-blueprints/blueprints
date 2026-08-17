package blueprint.workflowmodule.loanapproval.model;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate: one entity per workflow instance, holding everything the process
 * needs to know. There are no process variables - this is the single source of truth.
 *
 * <p>
 * This aggregate stores itself. Extending {@link PanacheEntityBase} adds {@code persist},
 * {@code delete} and the static finders to the entity, so nothing else is on the persistence
 * path: no repository, no persistence code of the application's own, no configuration
 * naming any of it. VanillaBP has to read and write the aggregate itself - it stores it when
 * a workflow is started, and it loads it before a {@code @WorkflowTask} method runs and
 * saves it afterwards - and it recognises this idiom while the application is built.
 * </p>
 *
 * <p>
 * Which idiom it picked is in the build log, one line per aggregate:
 * </p>
 *
 * <pre>
 * Using VanillaBP's Hibernate ORM Panache active record persistence for workflow aggregate
 * 'blueprint.workflowmodule.loanapproval.model.Aggregate'
 * </pre>
 *
 * <p>
 * That decision is made per aggregate rather than per application, and its order is fixed:
 * an {@code AggregatePersistenceAware} written by the application wins over everything, a
 * repository for this aggregate wins over the active record, and a Spring Data repository is
 * looked for last. An application may therefore mix idioms, and adding a repository for this
 * aggregate later is enough to move it onto that one - the log line says which way it went.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity
@Table(name = "LOAN_APPROVAL")
@Data
// The superclass has no state of its own, it only adds the operations. Saying so explicitly
// is what keeps the build free of Lombok's question about it.
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aggregate extends PanacheEntityBase {

  /**
   * The natural id of the use case. Using a business identifier instead of a generated one
   * makes a workflow started twice for the same business case a detectable duplicate.
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
   * Loads a loan approval by its natural id.
   *
   * <p>
   * The finder belongs on the aggregate because there is nowhere else for it to live, and it
   * is written out rather than left to the inherited {@code findByIdOptional}: that method is
   * generic and would hand the caller a {@code PanacheEntityBase}, which is not what anybody
   * wants to read. One method per query, named after the question it answers, is what a
   * repository would have given the application anyway.
   * </p>
   *
   * <p>
   * A caller needs a transaction or an active request context, because there is no longer a
   * class in between which could have brought one along. {@code Service} shows where that
   * belongs, and its integration test shows what it costs.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public static Optional<Aggregate> byId(
      final String loanRequestId) {

    return findByIdOptional(loanRequestId);

  }

}
