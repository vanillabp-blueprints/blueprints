package blueprint.simulator.esignature;

import java.time.Duration;
import java.util.Arrays;

/**
 * What the simulated e-signature service does with a document, chosen by the name of its
 * signer. A real service waits for a person, and the person signs, declines, or does nothing.
 * The real service may also fail. The simulator plays each of these, and a test chooses one by
 * the name it gives the signer. So a test describes its case with data, as it would for the
 * real system, and needs no extra call to set the simulator up.
 *
 * <p>
 * A name which is not in this list waits for the test API, like the real service waits for its
 * person. Nothing happens until a test or a person calls {@code sign}.
 * </p>
 */
public enum SignerBehaviour {

  /** Signs a moment after the document arrived, and the service reports it. */
  SIGNS("John Signing"),

  /** Declines to sign a moment after the document arrived, and the service reports it. */
  DECLINES("Jack Nosign"),

  /** The service fails when it gets the document: it answers with an error and keeps nothing. */
  BREAKS_THE_SERVICE("Jane Errornous"),

  /** Never does anything. The service takes the document and never reports about it. */
  NEVER_ANSWERS("Jim Avoidance"),

  /** Every other signer: the service takes the document and waits for the test API. */
  WAITS_FOR_THE_TEST_API(null);

  /**
   * How long a signer who acts on their own takes. Long enough that the application has finished
   * the step which sent the document, short enough that no test waits long.
   */
  public static final Duration MOMENT = Duration.ofMillis(250);

  private final String signer;

  SignerBehaviour(
      final String signer) {

    this.signer = signer;

  }

  /**
   * The behaviour the name of a signer stands for.
   *
   * @param signer The name of the signer of a document.
   * @return Its behaviour, {@link #WAITS_FOR_THE_TEST_API} for a name not in this list.
   */
  public static SignerBehaviour of(
      final String signer) {

    return Arrays
        .stream(values())
        .filter(behaviour -> behaviour.signer != null && behaviour.signer.equals(signer))
        .findFirst()
        .orElse(WAITS_FOR_THE_TEST_API);

  }

}
