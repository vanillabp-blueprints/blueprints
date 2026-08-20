package blueprint.workflowmodule.loanapproval.model;

/**
 * How risky a loan request is. This enum is the DATA MODEL, and the BPMN knows nothing
 * about it: no condition names it, no value of it appears in the model, and adding a value
 * is a change to Java and to the business code, not to a deployed process.
 *
 * <p>
 * It started out as a boolean {@code acceptable} in the first version of this application.
 * Turning that boolean into this enum is the change which would have forced a data
 * migration of every running workflow if the model had read the attribute - see the README.
 * </p>
 */
public enum RiskClass {

  /** Good enough to approve without anybody looking at it. */
  LOW,

  /** Not good enough to approve automatically, not bad enough to reject. */
  MEDIUM,

  /** Too risky. */
  HIGH

}
