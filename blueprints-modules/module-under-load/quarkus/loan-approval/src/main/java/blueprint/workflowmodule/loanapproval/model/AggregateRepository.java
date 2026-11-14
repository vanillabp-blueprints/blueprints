package blueprint.workflowmodule.loanapproval.model;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AggregateRepository implements PanacheRepositoryBase<Aggregate, String> {

  /**
   * The assessments a clerk may pick up, as a task list shows them.
   *
   * @return At most 50 open assessments.
   */
  public List<Aggregate> findTop50ByAssessmentTaskIdIsNotNullAndApprovalIsNull() {

    return find("assessmentTaskId is not null and approval is null")
        .page(0, 50)
        .list();

  }

  /**
   * How many loan approvals have not reached an end yet.
   *
   * @return The number of unfinished loan approvals.
   */
  public long countByPaidOutIsNullAndRejectionSentIsNull() {

    return count("paidOut is null and rejectionSent is null");

  }

}
