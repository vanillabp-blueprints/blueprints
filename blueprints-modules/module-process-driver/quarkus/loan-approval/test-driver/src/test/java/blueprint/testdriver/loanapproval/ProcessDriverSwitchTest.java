package blueprint.testdriver.loanapproval;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response.Status;

/**
 * The driver is off unless somebody switches it on. An application which has this module on its
 * classpath by mistake shows no endpoint of it.
 *
 * <p>
 * Quarkus builds a small application from this module for the test, without
 * {@code process-driver.enabled}. So the test sees what an application sees which is built
 * without the property.
 * </p>
 */
@QuarkusTest
public class ProcessDriverSwitchTest {

  /** How the application answers a path it does not know. */
  private static final int UNKNOWN_PATH = Status.NOT_FOUND.getStatusCode();

  @Test
  public void theDriverIsOffByDefault() {

    assertThat(Arc
        .container()
        .instance(ProcessDriver.class)
        .isAvailable()).as("the driver").isFalse();
    assertThat(Arc
        .container()
        .instance(ProcessDriverController.class)
        .isAvailable()).as("its REST API").isFalse();

  }

  @Test
  public void theRestApiOfTheDriverIsNotThereByDefault() {

    given()
        .get("/loan-approval/api/process-driver/states")
        .then()
        .statusCode(UNKNOWN_PATH);

  }

}
