package blueprint.simulator.loadrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import blueprint.workflowmodule.loanapproval.api.client.LoanApprovalApi;

/**
 * The end of a run stops the customers and the clerks while some of them still wait for an
 * answer of the application. A call cut off that way is not a failure of the application, so
 * the report must not count it. A call which fails while the run goes on still counts.
 *
 * <p>
 * The application here is a socket which takes every connection and never answers. So a
 * call is still going on for sure when the test stops the run. The client is the one the
 * simulator uses: the client of the JDK behind Spring's {@link RestClient}.
 * </p>
 */
public class StoppingTheRunTest {

  private static final Duration WAIT_AT_MOST = Duration.ofSeconds(30);

  private final List<Socket> connections = new CopyOnWriteArrayList<>();

  private ServerSocket silentApplication;

  @AfterEach
  public void closeTheSilentApplication() throws IOException {

    for (final var connection : connections) {
      connection.close();
    }
    if (silentApplication != null) {
      silentApplication.close();
    }

  }

  @Test
  public void aClerkWaitingForTheTaskListWhenTheRunStopsIsNoFailure() throws Exception {

    final var called = startTheSilentApplication();
    final var clerks = new Clerks(clientOf(silentApplication.getLocalPort()), profile());

    assertThat(called.await(WAIT_AT_MOST.toSeconds(), TimeUnit.SECONDS))
        .describedAs("a clerk looked at the task list")
        .isTrue();
    clerks.stop();
    waitUntilTheThreadsEnded("load-clerk-");

    assertThat(clerks.failed())
        .describedAs("decisions which failed")
        .isZero();

  }

  @Test
  public void aCustomerWaitingForAnAnswerWhenTheRunStopsIsNoFailure() throws Exception {

    final var called = startTheSilentApplication();
    final var customers = new Customers(clientOf(silentApplication.getLocalPort()), profile(), 1);
    customers.arrive(50);

    assertThat(called.await(WAIT_AT_MOST.toSeconds(), TimeUnit.SECONDS))
        .describedAs("a customer requested a loan")
        .isTrue();
    customers.stop();
    waitUntilTheThreadsEnded("load-customer-");

    assertThat(customers.failed())
        .describedAs("loan requests which failed")
        .isZero();

  }

  @Test
  public void aCallRefusedWhileTheRunGoesOnStillCounts() throws Exception {

    final int nobodyListens;
    try (var closedAgain = new ServerSocket(0)) {
      nobodyListens = closedAgain.getLocalPort();
    }
    final var clerks = new Clerks(clientOf(nobodyListens), profile());

    final var giveUpAt = System.nanoTime() + WAIT_AT_MOST.toNanos();
    while (clerks.failed() == 0 && System.nanoTime() < giveUpAt) {
      Thread.sleep(10);
    }
    clerks.stop();

    assertThat(clerks.failed())
        .describedAs("decisions which failed")
        .isPositive();

  }

  /**
   * A thread which was cut off may count its failure a moment after {@code stop()} returned.
   * Waiting for the threads makes the count final, so the test sees what the report would.
   */
  private static void waitUntilTheThreadsEnded(
      final String namePrefix) throws InterruptedException {

    for (final var thread : Thread
        .getAllStackTraces()
        .keySet()) {
      if (thread
          .getName()
          .startsWith(namePrefix)) {
        thread.join(WAIT_AT_MOST.toMillis());
      }
    }

  }

  private CountDownLatch startTheSilentApplication() throws IOException {

    silentApplication = new ServerSocket(0);
    final var called = new CountDownLatch(1);
    final var acceptor = new Thread(() -> {
      try {
        while (true) {
          connections.add(silentApplication.accept());
          called.countDown();
        }
      } catch (final IOException closed) {
        // The test is over.
      }
    }, "silent-application");
    acceptor.setDaemon(true);
    acceptor.start();
    return called;

  }

  private static LoanApprovalApi clientOf(
      final int port) {

    final var calls = RestClient
        .builder()
        .requestFactory(new JdkClientHttpRequestFactory())
        .baseUrl("http://localhost:"
            + port)
        .build();
    return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(calls))
        .build()
        .createClient(LoanApprovalApi.class);

  }

  /** One clerk who thinks only a moment, and customers who hand in a small document. */
  private static LoadProfile profile() {

    return new LoadProfile(
        "the profile of this test", "loan_approval", List
            .of(), new LoadProfile.Arrivals("even", 50), new LoadProfile.Aggregate(16), Map.of(Clerks.GATEWAY,
                Map.of(Clerks.APPROVED_FLOW, 0.8)), Map.of(Clerks.USER_TASK,
                    new LoadProfile.People(1, Duration.ofMillis(10), Duration.ZERO)), Map.of());

  }

}
