package blueprint.testdriver;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Turns a Java interface generated from an OpenAPI file into a client, as the rules of the
 * blueprints say: the generator writes a Spring HTTP interface, and a {@link RestClient} makes it
 * call a server. The process driver and the tests use the clients and never write a path, a
 * method or a status by hand.
 */
public final class GeneratedClients {

  private final HttpServiceProxyFactory factory;

  private GeneratedClients(
      final String baseUrl) {

    factory = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(RestClient.create(baseUrl)))
        .build();

  }

  /**
   * The clients of a server.
   *
   * @param baseUrl Where the server answers. The paths in the OpenAPI files are complete, so this
   *                is the address of the server and nothing more.
   * @return A factory of clients for this server.
   */
  public static GeneratedClients at(
      final String baseUrl) {

    return new GeneratedClients(baseUrl);

  }

  /**
   * The client of one API.
   *
   * @param <A> The generated interface.
   * @param api The generated interface.
   * @return The client.
   */
  public <A> A of(
      final Class<A> api) {

    return factory.createClient(api);

  }

}
