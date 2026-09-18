package blueprint.workflowmodule;

import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.mongodb.ConnectionString;

/**
 * The MongoDB every test of this Maven module talks to, started as a container.
 *
 * <p>
 * <strong>As a replica set</strong>, because MongoDB runs transactions only there. A
 * standalone deployment answers the first write of a transaction with an error, which is why
 * VanillaBP warns about it while starting up. This is the test-side half of that condition;
 * the other half is the transaction manager the workflow module defines.
 * </p>
 *
 * <p>
 * The class is a plain {@code @Configuration} in the base package, so component scanning picks
 * it up for every test of this module without a test having to name it. That keeps the shared
 * harness classes untouched: they know nothing about MongoDB, and they do not have to.
 * </p>
 *
 * <p>
 * The container is started once for the JVM and left to Testcontainers' own cleanup rather
 * than stopped per test class. Starting a replica set costs a few seconds, and paying that per
 * test class is what makes people distrust integration tests.
 * </p>
 */
@Configuration
public class MongoDbForTests {

  private static final String DATABASE = "loan-approval";

  /**
   * The image the container runs, overridable with {@code -Dmongodb.image=mongo:7.0}.
   *
   * <p>
   * The version is written down, so a test run says which MongoDB it ran against. It is
   * 8.2 and not 8.0 because MongoDB 8.0 refuses to start on a Linux kernel 6.19 or newer, see
   * <a href="https://jira.mongodb.org/browse/SERVER-121912">SERVER-121912</a>. The container
   * ends right after it was started and the failing test says nothing about the reason. Where
   * a machine needs another version, pass the property instead of changing this file.
   * </p>
   */
  private static final String IMAGE = System.getProperty("mongodb.image", "mongo:8.2");

  private static final MongoDBContainer MONGO_DB = new MongoDBContainer(
      DockerImageName.parse(IMAGE))
      .withReplicaSet();

  static {
    MONGO_DB.start();
  }

  /**
   * Points the MongoDB client at the container instead of at the address the application
   * configures.
   *
   * @return The customizer applied to the client the platform builds.
   */
  @Bean
  public MongoClientSettingsBuilderCustomizer mongoDbOfTheContainer() {

    return builder -> builder.applyConnectionString(
        new ConnectionString(MONGO_DB.getReplicaSetUrl(DATABASE)));

  }

}
