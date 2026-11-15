package blueprint.externalsystems.creditrating;

/**
 * A credit score, in the type the application wants to see. The adapter translates whatever the
 * credit rating sends into this.
 *
 * @param value The higher, the more likely the customer pays back.
 */
public record CreditScore(
                          int value) {
}
