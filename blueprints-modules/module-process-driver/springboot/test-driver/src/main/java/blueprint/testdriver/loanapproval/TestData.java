package blueprint.testdriver.loanapproval;

/**
 * What a scenario says about the loan request it drives. Every value has a default which makes
 * sense for the business: a customer with a good credit score asks for an amount below the limit
 * of the risk assessment. A test changes only what its scenario is about, for example the amount
 * which needs a risk assessment.
 *
 * @param amount      The amount the customer asks for.
 * @param creditScore The score the simulated credit rating answers for the customer.
 */
public record TestData(
                       int amount,
                       int creditScore) {

  /** An amount below the limit of the risk assessment, which is 10000 by default. */
  public static final int SMALL_AMOUNT = 5000;

  /** An amount above the limit of the risk assessment. */
  public static final int LARGE_AMOUNT = 25000;

  /** A score well above the minimum the module asks for, which is 600 by default. */
  public static final int GOOD_CREDIT_SCORE = 720;

  /**
   * The defaults: a good customer asks for a small amount.
   *
   * @return The test data of the plain case.
   */
  public static TestData defaults() {

    return new TestData(SMALL_AMOUNT, GOOD_CREDIT_SCORE);

  }

  /**
   * The same test data with another amount.
   *
   * @param amount The amount the customer asks for.
   * @return The changed test data.
   */
  public TestData withAmount(
      final int amount) {

    return new TestData(amount, creditScore);

  }

  /**
   * The same test data with another credit score.
   *
   * @param creditScore The score the credit rating answers.
   * @return The changed test data.
   */
  public TestData withCreditScore(
      final int creditScore) {

    return new TestData(amount, creditScore);

  }

}
