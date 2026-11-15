package blueprint.simulator.loadrun;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Asks the JVM of the application for a full garbage collection, over JMX.
 *
 * <p>
 * The run does this twice, after the warm up and after the drain. Only after a full collection
 * does the heap hold nothing but the live set, so only then are the two numbers comparable.
 * </p>
 *
 * <p>
 * JMX is the management interface every JVM has. An operator uses the same one, for example
 * with the button "Perform GC" of JConsole. So the application needs no endpoint of its own for
 * this. It only has to be started with a JMX port, and the README shows how.
 * </p>
 */
final class FullCollection {

  private final String address;

  /**
   * @param address Where the JVM of the application answers on JMX, as {@code host:port}.
   */
  FullCollection(
      final String address) {

    this.address = address;

  }

  /**
   * Asks for a full collection and waits until the JVM has done it.
   *
   * @throws IllegalStateException If nothing answers on the JMX port. The message says how to
   *                               start the application with one.
   */
  void ask() {

    try (var connector = JMXConnectorFactory.connect(new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"
        + address
        + "/jmxrmi"))) {

      ManagementFactory
          .newPlatformMXBeanProxy(
              connector.getMBeanServerConnection(), ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class)
          .gc();

    } catch (IOException e) {
      throw new IllegalStateException("The application does not answer on JMX at '"
          + address
          + "'. The run asks its JVM for a full garbage collection there. Start the application"
          + " with '-Dcom.sun.management.jmxremote.port=9010"
          + " -Dcom.sun.management.jmxremote.rmi.port=9010"
          + " -Dcom.sun.management.jmxremote.host=localhost -Djava.rmi.server.hostname=localhost"
          + " -Dcom.sun.management.jmxremote.authenticate=false"
          + " -Dcom.sun.management.jmxremote.ssl=false', or set 'load-run.application.jmx-address'"
          + " to where its JMX port is.", e);
    }

  }

}
