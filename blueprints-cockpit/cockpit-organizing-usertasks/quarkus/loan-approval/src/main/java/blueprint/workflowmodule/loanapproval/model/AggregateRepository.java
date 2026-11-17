package blueprint.workflowmodule.loanapproval.model;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {

  /**
   * The users who worked on one user task of a loan approval, and nothing else of the case.
   * The check of who may read a form asks this on every request, so it should not load the
   * whole aggregate.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @return The user ids, empty if the task is unknown or nobody worked on it yet.
   */
  public List<String> findEditors(
      final String loanRequestId,
      final String taskId) {

    return getEntityManager()
        .createQuery("select e from Aggregate a join a.tasks t join t.editors e"
            + " where a.loanRequestId = :loanRequestId and t.taskId = :taskId", String.class)
        .setParameter("loanRequestId", loanRequestId)
        .setParameter("taskId", taskId)
        .getResultList();

  }

}
