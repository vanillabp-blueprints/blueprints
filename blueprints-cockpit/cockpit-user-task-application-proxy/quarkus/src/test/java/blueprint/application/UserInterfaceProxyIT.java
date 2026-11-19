package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.exception.NotFoundException;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;

/**
 * Runs the application in front of the real nginx of the frontend, and asks it what the
 * cockpit asks: the manifest of the user interface and every file the manifest names.
 *
 * <p>
 * The nginx is the image the frontend builds, not a stand-in with test files. What this test
 * proves is that the application and that image fit together: the same paths, the same
 * manifest, the headers of the nginx passed on. A stand-in would only prove that the route
 * forwards something.
 * </p>
 *
 * <p>
 * The route comes from the support library of the Business Cockpit, which the workflow module
 * depends on. So this test also proves that the library brings it. The application has no route
 * of its own and only names the address of the nginx.
 * </p>
 *
 * <p>
 * The image is not pulled. It is built by the frontend, on this machine or by CI before this
 * test runs. The property {@code user-interface.image} names it, and the POM sets it to the
 * version of the frontend this application belongs to.
 * </p>
 */
@QuarkusTest
@WithTestResource(UserInterfaceProxyIT.TheNginxOfTheFrontend.class)
public class UserInterfaceProxyIT {

  private static final String IMAGE = System.getProperty(
      "user-interface.image",
      "vanillabp-blueprints/cockpit-user-task-ui:1.0.0-SNAPSHOT");

  /** The files a manifest names, such as "entries/UserTaskForm-OWGXIQUD.js". */
  private static final Pattern FILE_IN_MANIFEST = Pattern.compile("\"([^\"]+\\.js)\"");

  private final HttpClient http = HttpClient.newHttpClient();

  /** The application under test, on its free port. */
  @TestHTTPResource
  URI application;

  /**
   * Starts the nginx of the frontend before the application starts, and hands its address to the
   * application.
   */
  public static class TheNginxOfTheFrontend implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> nginx;

    @Override
    public Map<String, String> start() {

      try {
        DockerClientFactory
            .instance()
            .client()
            .inspectImageCmd(IMAGE)
            .exec();
      } catch (NotFoundException e) {
        throw new IllegalStateException("The image '"
            + IMAGE
            + "' is not on this machine. Build it in the frontend with 'npm run build'"
            + " and 'npm run build:image', or name another one with -Duser-interface.image=...");
      }

      nginx = new GenericContainer<>(DockerImageName.parse(IMAGE))
          .withExposedPorts(80)
          .waitingFor(Wait.forHttp("/loan-approval/bc-manifest.json"));
      nginx.start();

      return Map.of(
          "loan-approval.user-interface.address",
          "http://"
              + nginx.getHost()
              + ":"
              + nginx.getMappedPort(80));

    }

    @Override
    public void stop() {

      if (nginx != null) {
        nginx.stop();
      }

    }

  }

  @Test
  public void theManifestComesFromTheNginx() throws Exception {

    final var manifest = get("/loan-approval/bc-manifest.json");

    assertThat(manifest.statusCode()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(manifest.body()).contains("\"entries\"");
    // only the nginx sends this header, so the answer came from there
    assertThat(manifest
        .headers()
        .firstValue("Cache-Control")).hasValue("no-cache");

  }

  @Test
  public void everyFileTheManifestNamesComesFromTheNginx() throws Exception {

    final var manifest = get("/loan-approval/bc-manifest.json").body();
    final var files = FILE_IN_MANIFEST
        .matcher(manifest)
        .results()
        .map(match -> match.group(1))
        .toList();

    assertThat(files)
        .describedAs("the files named in "
            + manifest)
        .isNotEmpty();

    for (final var file : files) {
      final var answer = get("/loan-approval/"
          + file);
      assertThat(answer.statusCode())
          .describedAs(file)
          .isEqualTo(Response.Status.OK.getStatusCode());
      assertThat(answer
          .headers()
          .firstValue("Content-Type"))
          .describedAs(file)
          .hasValueSatisfying(type -> assertThat(type).contains("javascript"));
    }

  }

  @Test
  public void theApiOfTheModuleStaysInTheApplication() throws Exception {

    final var answer = get("/loan-approval/api/loan-requests/nobody");

    assertThat(answer.statusCode()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(answer.body()).isEqualTo("unknown loan request 'nobody'");

  }

  private HttpResponse<String> get(
      final String path) throws Exception {

    return http.send(
        HttpRequest
            .newBuilder(application.resolve(path))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

  }

}
