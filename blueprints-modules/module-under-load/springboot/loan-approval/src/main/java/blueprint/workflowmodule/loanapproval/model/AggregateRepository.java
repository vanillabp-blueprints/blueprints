package blueprint.workflowmodule.loanapproval.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AggregateRepository extends JpaRepository<Aggregate, String> {

  /**
   * The assessments a clerk may pick up, as a task list shows them.
   *
   * @return At most 50 open assessments.
   */
  List<Aggregate> findTop50ByAssessmentTaskIdIsNotNullAndApprovalIsNull();

  /**
   * How many loan approvals have not reached an end yet.
   *
   * @return The number of unfinished loan approvals.
   */
  long countByPaidOutIsNullAndRejectionSentIsNull();

}
