package blueprint.workflowmodule.loanapproval.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AggregateRepository extends JpaRepository<Aggregate, String> {

  /**
   * The loan request whose user task "Assess risk" is open under this id. A form knows the id
   * of its user task and nothing else, so this is how it finds its loan request.
   *
   * @param assessRiskTaskId The id of the user task.
   * @return The loan request, if the task is open.
   */
  Optional<Aggregate> findByAssessRiskTaskId(
      String assessRiskTaskId);

  /**
   * The loan request whose user task "Check documents" is open under this id.
   *
   * @param checkDocumentsTaskId The id of the user task.
   * @return The loan request, if the task is open.
   */
  Optional<Aggregate> findByCheckDocumentsTaskId(
      String checkDocumentsTaskId);

}
