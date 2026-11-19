package blueprint.workflowmodule.loanapproval.portfolioreview.model;

/**
 * Why a portfolio review runs. Each start event of the process has its own
 * {@code @WorkflowStartedByBpms} method, and each method writes its own value.
 */
public enum Occasion {

  /** The timer start event fired. */
  SCHEDULED,

  /** Somebody broadcast the signal, and the signal start event received it. */
  REQUESTED

}
