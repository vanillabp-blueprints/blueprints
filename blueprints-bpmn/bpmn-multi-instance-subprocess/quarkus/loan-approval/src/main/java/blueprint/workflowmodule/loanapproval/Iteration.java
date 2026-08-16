package blueprint.workflowmodule.loanapproval;

/**
 * Where one call of the innermost task sits: which region and which partner, each with
 * the position of its iteration.
 *
 * <p>
 * This is what {@link IterationResolver} builds out of the two iterations the BPMS
 * reports. A method could ask for the six values one by one; asking for this record says
 * what they mean together.
 * </p>
 *
 * @param regionId    The region the subprocess is iterating for.
 * @param regionIndex Which region this is, counted from zero.
 * @param regions     How many regions there are.
 * @param partnerId   The partner this iteration of the task asks.
 * @param partnerIndex Which partner this is, counted from zero.
 * @param partners    How many partners there are.
 */
public record Iteration(
                        String regionId,
                        int regionIndex,
                        int regions,
                        String partnerId,
                        int partnerIndex,
                        int partners) {
}
