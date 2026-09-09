package blueprint.workflowmodule.loanapproval.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The additional checks the loan approval can run, and the BPMN element each of them is.
 *
 * <p>
 * This is the one place the element ids of the ad-hoc subprocess are written down. The
 * model activates activities by their id, so those ids are a contract between the BPMN file
 * and the code, and everything else in this application speaks about a check by its name.
 * </p>
 *
 * <p>
 * The decision table names the same ids in its output column, which is the other half of
 * the contract and the reason the two decision makers are interchangeable: whoever picks,
 * what arrives at the subprocess is a list of these ids.
 * </p>
 */
public enum Check {

  /** The income of the applicant. The decision table picks this one for every request. */
  INCOME("Check_Income"),

  /** A fraud check, which the decision table adds above a threshold. */
  FRAUD("Check_Fraud"),

  /** The collateral offered, which only a case worker ever asks for. */
  COLLATERAL("Check_Collateral");

  private final String elementId;

  Check(
      final String elementId) {

    this.elementId = elementId;

  }

  /** @return The id of the BPMN element inside the ad-hoc subprocess. */
  public String getElementId() {

    return elementId;

  }

  /**
   * Turns what somebody picked into the list the model reads.
   *
   * <p>
   * A mutable list on purpose: it is stored as an attribute of the workflow aggregate, and
   * a persistence layer takes such a collection over and changes it while it saves.
   * </p>
   *
   * @param checks The checks to run.
   * @return Their element ids, in the order they were given.
   */
  public static List<String> elementIdsOf(
      final List<Check> checks) {

    return new ArrayList<>(checks
        .stream()
        .map(Check::getElementId)
        .toList());

  }

  /**
   * Reads what arrived through the API, so a typo becomes a message rather than a workflow
   * standing in front of an activity nobody named.
   *
   * @param names The names of the checks, as the URL spelled them.
   * @return The checks.
   */
  public static List<Check> of(
      final String... names) {

    return Arrays
        .stream(names)
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .map(name -> Check.valueOf(name.toUpperCase()))
        .toList();

  }

}
