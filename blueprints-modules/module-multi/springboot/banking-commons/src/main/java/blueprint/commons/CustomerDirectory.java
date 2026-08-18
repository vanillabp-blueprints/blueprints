package blueprint.commons;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * A client for a system next door, and the second kind of code a shared library may hold:
 * both use cases need a customer's name, and neither of them should own the way to get it.
 *
 * <p>
 * It answers from a map here, because a blueprint has no system next door. In an
 * application this is where the REST client, its timeouts and its error handling would
 * live, once, instead of in each workflow module.
 * </p>
 */
@Slf4j
public class CustomerDirectory {

  private static final Map<String, String> NAMES = Map
      .of(
          "C-1001", "Ada Lovelace",
          "C-1002", "Grace Hopper");

  /**
   * The name of a customer.
   *
   * @param customerId The customer's id.
   * @return The name, or the id itself if the directory does not know it.
   */
  public String nameOf(
      final String customerId) {

    final var name = NAMES.getOrDefault(customerId, customerId);

    log.debug("Customer '{}' is '{}'", customerId, name);

    return name;

  }

}
