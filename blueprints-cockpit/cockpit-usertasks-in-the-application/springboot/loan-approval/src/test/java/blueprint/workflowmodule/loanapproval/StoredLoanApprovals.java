package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.usertask.Task;

/**
 * Reads a loan approval for a test, with every user task and its data. The application loads
 * the tasks and their data only when it reads them, inside a transaction. A test looks at all
 * of them after the transaction, so this bean loads them all before it ends.
 */
@Component
public class StoredLoanApprovals {

  @Autowired
  private AggregateRepository loanApprovals;

  /**
   * The loan approval as it is stored right now.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval with all its tasks and their data, if it exists.
   */
  @Transactional(readOnly = true)
  public Optional<Aggregate> find(
      final String loanRequestId) {

    final var loanRequest = loanApprovals.findById(loanRequestId);
    loanRequest.ifPresent(found -> found
        .getTasks()
        .values()
        .forEach(Task::getData));
    return loanRequest;

  }

}
