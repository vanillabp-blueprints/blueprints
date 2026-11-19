package blueprint.testdriver;

import java.net.URI;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

/**
 * Turns a Java interface generated from an OpenAPI file into a client, as the rules of the
 * blueprints say: the generator writes an interface of Jakarta REST, and the REST client of Quarkus
 * makes it call a server. The process driver and the tests use the clients and never write a path,
 * a method or a status by hand.
 *
 * <p>
 * The REST client exists only while Quarkus runs. So a client is made when {@link #of(Class)} is
 * called, not when this class is made.
 * </p>
 */
public final class GeneratedClients {

  private final URI baseUrl;

  private GeneratedClients(
      final String baseUrl) {

    this.baseUrl = URI.create(baseUrl);

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

    return QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(baseUrl)
        .build(api);

  }

}
