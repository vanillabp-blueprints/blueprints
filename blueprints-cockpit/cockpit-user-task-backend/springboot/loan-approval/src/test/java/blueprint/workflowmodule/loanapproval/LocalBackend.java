package blueprint.workflowmodule.loanapproval;

import org.springframework.boot.SpringApplication;

import blueprint.workflowmodule.TestApplication;

/**
 * Runs this workflow module on your machine, for somebody who works on its user interface.
 *
 * <p>
 * The backend has no application of its own. The application which runs the workflow module
 * is a repository of its own, and it needs the user interface, which is built after the
 * backend. So this class starts the module the way its test does: with the
 * {@link TestApplication}, the database in memory and the test configuration. That
 * configuration reports to a dev shell simulator on its default port 8079. The module answers
 * on port 8080.
 * </p>
 *
 * <pre>
 * mvn -pl loan-approval spring-boot:test-run -Dspring-boot.run.profiles=camunda7
 * </pre>
 */
public final class LocalBackend {

  private LocalBackend() {
  }

  /**
   * Starts the workflow module.
   *
   * @param args The command line arguments, passed on to Spring Boot.
   */
  public static void main(
      final String[] args) {

    SpringApplication.run(TestApplication.class, args);

  }

}
