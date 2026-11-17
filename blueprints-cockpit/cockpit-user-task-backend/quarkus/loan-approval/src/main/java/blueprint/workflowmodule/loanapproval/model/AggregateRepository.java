package blueprint.workflowmodule.loanapproval.model;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Loading and storing the workflow aggregate, for the application and for VanillaBP. VanillaBP
 * recognises the repository of an aggregate, so no application code says how it reads and writes
 * the aggregate.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@ApplicationScoped
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {

  /**
   * The loan request whose user task "Assess risk" is open under this id. A form knows the id
   * of its user task and nothing else, so this is how it finds its loan request.
   *
   * @param assessRiskTaskId The id of the user task.
   * @return The loan request, if the task is open.
   */
  public Optional<Aggregate> findByAssessRiskTaskId(
      final String assessRiskTaskId) {

    return find("assessRiskTaskId", assessRiskTaskId).firstResultOptional();

  }

  /**
   * The loan request whose user task "Check documents" is open under this id.
   *
   * @param checkDocumentsTaskId The id of the user task.
   * @return The loan request, if the task is open.
   */
  public Optional<Aggregate> findByCheckDocumentsTaskId(
      final String checkDocumentsTaskId) {

    return find("checkDocumentsTaskId", checkDocumentsTaskId).firstResultOptional();

  }

}
