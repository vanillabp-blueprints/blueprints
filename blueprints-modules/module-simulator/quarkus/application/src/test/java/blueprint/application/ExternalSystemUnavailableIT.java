package blueprint.application;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * What a user sees when a system around the application does not answer.
 *
 * <p>
 * The test runs the application in the profile {@code real}, the way it runs in production. The
 * addresses of the real systems come from the environment there. Here they point at a port where
 * nothing answers. So no simulator is needed, and none may answer.
 * </p>
 */
@QuarkusTest
@WithTestResource(ExternalSystemUnavailableIT.TheRealSystemsAreMissing.class)
public class ExternalSystemUnavailableIT {

  /**
   * The environment variables of the profile 'real', as an operator would set them.
   */
  public static class TheRealSystemsAreMissing implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {

      // A port which was free a moment ago, so nothing answers there.
      final var nowhere = "http://localhost:"
          + freePort();

      return Map.of(
          "CREDIT_RATING_URL", nowhere,
          "E_SIGNATURE_URL", nowhere,
          "E_SIGNATURE_CALLBACK_URL", "http://localhost:8080/e-signature/callback",
          "BUSINESS_COCKPIT_URL", nowhere);

    }

    @Override
    public void stop() {

      // Nothing was started.

    }

  }

  @Test
  @SuppressWarnings("unchecked")
  public void aMissingCreditRatingIsA503WhichNamesItsRole() {

    final var response = given().get("/api/loan-approval/request?customerId=C-1001&amount=5000");

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat((Map<String, Object>) response.as(Map.class))
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
