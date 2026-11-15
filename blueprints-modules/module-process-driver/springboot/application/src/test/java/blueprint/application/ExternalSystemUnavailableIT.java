package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * What a user sees when a system around the application does not answer.
 *
 * <p>
 * The test runs the application in the profile {@code real}, the way it runs in production. The
 * addresses of the real systems come from the environment there. Here they point at a port where
 * nothing answers. So no simulator is needed, and none may answer.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ExternalSystemUnavailableIT {

  /** A port which was free a moment ago, so nothing answers there. */
  private static final String NOWHERE = "http://localhost:"
      + freePort();

  @Value("${local.server.port}")
  private int port;

  /**
   * The environment variables of the profile 'real', as an operator would set them.
   *
   * @param properties The properties of the application under test.
   */
  @DynamicPropertySource
  static void theRealSystemsAreMissing(
      final DynamicPropertyRegistry properties) {

    properties.add("CREDIT_RATING_URL", () -> NOWHERE);
    properties.add("E_SIGNATURE_URL", () -> NOWHERE);
    properties.add("E_SIGNATURE_CALLBACK_URL", () -> "http://localhost:8080/e-signature/callback");
    properties.add("BUSINESS_COCKPIT_URL", () -> NOWHERE);

  }

  @Test
  @SuppressWarnings("unchecked")
  public void aMissingCreditRatingIsA503WhichNamesItsRole() {

    final var answer = RestClient
        .create("http://localhost:"
            + port)
        .get()
        .uri("/api/loan-approval/request?customerId=C-1001&amount=5000")
        .exchange((
            request,
            response) -> Map.of(
                "status", response.getStatusCode().value(),
                "body", response.bodyTo(Map.class)));

    assertThat(answer.get("status")).isEqualTo(503);
    assertThat((Map<String, Object>) answer.get("body"))
        .containsEntry("externalSystem", "credit-rating")
        .containsEntry("status", 503);

  }

  private static int freePort() {

    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port", e);
    }

  }

}
