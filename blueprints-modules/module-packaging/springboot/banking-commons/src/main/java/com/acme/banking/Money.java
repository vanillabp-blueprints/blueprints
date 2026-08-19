package com.acme.banking;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * An amount of money, the kind of value object two use cases may share without coupling
 * them: it carries no state of a business case and no decision of one.
 *
 * <p>
 * This is what belongs into a library shared by workflow modules. What does not is on the
 * list in the README, and the shortest version of it is: nothing a process is made of.
 * </p>
 *
 * @param amount   The amount.
 * @param currency The ISO currency code.
 */
public record Money(BigDecimal amount, String currency) {

  /**
   * An amount in Euro, the currency this blueprint does business in.
   *
   * @param amount The amount.
   * @return The amount as money.
   */
  public static Money euro(
      final long amount) {

    return new Money(BigDecimal.valueOf(amount), "EUR");

  }

  /**
   * The amount as a person reads it, for logs and for the API.
   *
   * @return e.g. "5,000.00 EUR".
   */
  public String formatted() {

    return NumberFormat
        .getNumberInstance(Locale.US)
        .format(amount)
        + " "
        + currency;

  }

}
