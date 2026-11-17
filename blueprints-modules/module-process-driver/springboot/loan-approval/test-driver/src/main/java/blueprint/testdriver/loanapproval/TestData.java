package blueprint.testdriver.loanapproval;

/**
 * What a scenario says about the loan request it drives. Every value has a default which makes
 * sense for the business: a customer with a good credit score asks for an amount below the limit
 * of the risk assessment. A test changes only what its scenario is about, for example the amount
 * which needs a risk assessment.
 *
 * <p>
 * The name of the customer also decides what the simulated e-signature service does with the
 * contract, because the customer signs it. The names below are the ones the simulator acts on.
 * Every other name, like the default, waits until a test signs through the test API. A step which
 * needs a signed contract chooses {@link #JOHN_SIGNING} by itself, and a step which needs a
 * declined one chooses {@link #JACK_NOSIGN}.
 * </p>
 *
 * @param amount       The amount the customer asks for.
 * @param creditScore  The score the simulated credit rating answers for the customer.
 * @param customerName The name of the customer, who signs the contract.
 */
public record TestData(
                       int amount,
                       int creditScore,
                       String customerName) {

  /** An amount below the limit of the risk assessment, which is 10000 by default. */
  public static final int SMALL_AMOUNT = 5000;

  /** An amount above the limit of the risk assessment. */
  public static final int LARGE_AMOUNT = 25000;

  /** A score well above the minimum the module asks for, which is 600 by default. */
  public static final int GOOD_CREDIT_SCORE = 720;

  /** A customer who signs only when a test or a person says so through the test API. */
  public static final String JANE_DOE = "Jane Doe";

  /** A customer who signs a moment after the contract arrived at the e-signature service. */
  public static final String JOHN_SIGNING = "John Signing";

  /** A customer who declines to sign a moment after the contract arrived. */
  public static final String JACK_NOSIGN = "Jack Nosign";

  /** A customer whose contract makes the e-signature service fail with an error. */
  public static final String JANE_ERRORNOUS = "Jane Errornous";

  /** A customer who never does anything with the contract, so the service never reports. */
  public static final String JIM_AVOIDANCE = "Jim Avoidance";

  /**
   * The defaults: a good customer asks for a small amount and signs when the test says so.
   *
   * @return The test data of the plain case.
   */
  public static TestData defaults() {

    return new TestData(SMALL_AMOUNT, GOOD_CREDIT_SCORE, JANE_DOE);

  }

  /**
   * The same test data with another amount.
   *
   * @param amount The amount the customer asks for.
   * @return The changed test data.
   */
  public TestData withAmount(
      final int amount) {

    return new TestData(amount, creditScore, customerName);

  }

  /**
   * The same test data with another credit score.
   *
   * @param creditScore The score the credit rating answers.
   * @return The changed test data.
   */
  public TestData withCreditScore(
      final int creditScore) {

    return new TestData(amount, creditScore, customerName);

  }

  /**
   * The same test data with another customer, who signs the contract.
   *
   * @param customerName The name of the customer, for example {@link #JACK_NOSIGN}.
   * @return The changed test data.
   */
  public TestData withCustomerName(
      final String customerName) {

    return new TestData(amount, creditScore, customerName);

  }

}
