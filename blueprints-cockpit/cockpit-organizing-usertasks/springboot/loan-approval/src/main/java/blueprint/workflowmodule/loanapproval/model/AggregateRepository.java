package blueprint.workflowmodule.loanapproval.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AggregateRepository extends JpaRepository<Aggregate, String> {

  /**
   * The users who worked on one user task of a loan approval, and nothing else of the case.
   * The check of who may read a form asks this on every request, so it should not load the
   * whole aggregate.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The user ids, empty if the task is unknown or nobody worked on it yet.
   */
  @Query("select e from Aggregate a join a.tasks t join t.editors e"
      + " where a.loanRequestId = :loanRequestId and t.taskId = :taskId")
  List<String> findEditors(
      @Param("loanRequestId") String loanRequestId,
      @Param("taskId") String taskId);

}
