package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.persistence.AggregateStore;
import blueprint.workflowmodule.loanapproval.persistence.DeliveryLog;
import blueprint.workflowmodule.loanapproval.persistence.PhaseTwoStore;
import blueprint.workflowmodule.loanapproval.persistence.UnitOfWork;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS and
 * waits for the process to have done its work.
 *
 * <p>
 * The first test is the one of the base blueprint, reading the aggregate through the store of
 * the application instead of a repository. The other two are why this blueprint exists: they
 * prove that VanillaBP really ran its work inside the unit of work of this application, and
 * that a rollback of that unit of work leaves none of the three stores written.
 * </p>
 *
 * <p>
 * Counting is the only way to prove the first of those. A workflow which simply works proves
 * nothing: the platform has a transaction of its own, and an application which believes its
 * unit of work is being used while something else is committing would notice at the worst
 * possible moment.
 * </p>
 */
@SpringBootTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service service;

  @Autowired
  private AggregateStore loanApprovals;

  @Autowired
  private PhaseTwoStore scheduledStarts;

  @Autowired
  private DeliveryLog deliveredTasks;

  @Autowired
  private UnitOfWork unitOfWork;

  @Test
  public void theServiceTaskFillsTheAggregate() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        loanApprovals::find,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);

  }

  @Test
  public void vanillaBpWorksInsideTheUnitOfWorkOfThisApplication() {

    final var loanRequestId = UUID.randomUUID().toString();
    unitOfWork.resetCounters();

    service.initiateLoanApproval(loanRequestId, 7000);

    awaitAggregate(
        loanApprovals::find,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    // One unit of work is the application's own, around the start. The others are
    // VanillaBP's: the second phase of the start, and the delivery of the service task.
    assertThat(unitOfWork.getOpened())
        .describedAs("units of work opened for one workflow")
        .isGreaterThan(1);
    assertThat(unitOfWork.getCommitted())
        .describedAs("units of work committed")
        .isEqualTo(unitOfWork.getOpened());
    assertThat(unitOfWork.getRolledBack())
        .describedAs("units of work rolled back")
        .isZero();
    assertThat(deliveredTasks.size())
        .describedAs("the delivery of the service task, remembered by the application")
        .isPositive();

  }

  @Test
  public void aFailedStartLeavesNothingBehind() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var scheduledBefore = scheduledStarts
        .getScheduled()
        .size();
    unitOfWork.resetCounters();

    // The application opens its unit of work, VanillaBP joins it: the aggregate and the
    // entry scheduling the second phase of the start are written inside it. Then the
    // application decides the business case does not happen after all.
    assertThatThrownBy(
        () -> unitOfWork.requireNew(() -> {
          service.initiateLoanApproval(loanRequestId, 5000);
          throw new IllegalStateException("the application aborts after the start");
        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(loanApprovals.find(loanRequestId))
        .describedAs("the aggregate of a rolled-back start")
        .isEmpty();
    assertThat(scheduledStarts.getScheduled())
        .describedAs("the scheduled second phase of a rolled-back start")
        .hasSize(scheduledBefore);
    assertThat(unitOfWork.getRolledBack())
        .describedAs("units of work rolled back")
        .isPositive();

  }

}
