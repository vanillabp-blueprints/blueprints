package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.usertask.Task;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Reads a loan approval for a test, with every user task and its data. The application loads
 * the tasks and their data only when it reads them, inside a transaction. A test looks at all
 * of them after the transaction, so this bean loads them all before it ends.
 */
@ApplicationScoped
public class StoredLoanApprovals {

  @Inject
  AggregateRepository loanApprovals;

  /**
   * The loan approval as it is stored right now.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval with all its tasks and their data, if it exists.
   */
  @Transactional
  public Optional<Aggregate> find(
      final String loanRequestId) {

    final var loanRequest = loanApprovals.findByIdOptional(loanRequestId);
    loanRequest.ifPresent(found -> found
        .getTasks()
        .values()
        .forEach(Task::getData));
    return loanRequest;

  }

}
