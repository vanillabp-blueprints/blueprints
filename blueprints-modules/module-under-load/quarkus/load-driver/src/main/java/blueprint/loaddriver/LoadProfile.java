package blueprint.loaddriver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * The load profile: what a BPMN model does not say about the load it gets. It is read from a
 * YAML file next to the BPMN files of the workflow module.
 *
 * <p>
 * The keys name BPMN elements by their ids, so a tool which reads the model can ask a person
 * for exactly these values and write the same file. A key this class does not know is an
 * error, because a typo would otherwise silently fall back to nothing.
 * </p>
 *
 * @param source     Where the profile was read from, for the messages.
 * @param process    The BPMN process id the profile is about.
 * @param hotPath    The BPMN elements most workflows pass, in their order.
 * @param arrivals   How customers arrive.
 * @param aggregate  How big a workflow aggregate is.
 * @param gateways   The share of each outgoing flow, per gateway id.
 * @param people     The persons who work on each user task, per user task id.
 * @param neighbours The neighbour systems, per id of the element which waits for their answer.
 */
public record LoadProfile(
                          String source,
                          String process,
                          List<String> hotPath,
                          Arrivals arrivals,
                          Aggregate aggregate,
                          Map<String, Map<String, Double>> gateways,
                          Map<String, People> people,
                          Map<String, Neighbour> neighbours) {

  /**
   * How customers arrive.
   *
   * @param pattern   {@code random} for gaps which vary like real arrivals do, {@code even} for
   *                  the same gap every time.
   * @param perSecond How many workflows start per second in the steady stage.
   */
  public record Arrivals(
                         String pattern,
                         double perSecond) {
  }

  /**
   * How big a workflow aggregate is.
   *
   * @param sizeBytes The size of what a customer hands in, in bytes.
   */
  public record Aggregate(
                          int sizeBytes) {
  }

  /**
   * The persons who work on one user task.
   *
   * @param persons         How many of them work at the same time.
   * @param thinkTime       How long one holds a task on average.
   * @param thinkTimeSpread How far a single think time is off the average, at most.
   */
  public record People(
                       int persons,
                       Duration thinkTime,
                       Duration thinkTimeSpread) {
  }

  /**
   * A neighbour system which answers with a message.
   *
   * @param name          A name for the report.
   * @param latency       How long the answer takes on average.
   * @param latencySpread How far a single answer is off the average, at most.
   * @param noAnswerShare The share of questions which never get an answer.
   */
  public record Neighbour(
                          String name,
                          Duration latency,
                          Duration latencySpread,
                          double noAnswerShare) {
  }

  /**
   * Reads a load profile from the class path.
   *
   * @param resource The path of the file on the class path.
   * @return The profile.
   * @throws IllegalStateException If the file is missing or a value is wrong. The message names
   *                               the key and what is expected there.
   */
  public static LoadProfile read(
      final String resource) {

    final var loader = Thread
        .currentThread()
        .getContextClassLoader();
    try (InputStream in = loader.getResourceAsStream(resource)) {

      if (in == null) {
        throw new IllegalStateException("The load profile '"
            + resource
            + "' is not on the class path. It is a YAML file next to the BPMN files of the"
            + " workflow module, and 'load-run.profile' names it.");
      }
      final Map<String, Object> root = new Yaml().load(in);
      return new Reader(resource).profile(root);

    } catch (final IOException e) {
      throw new UncheckedIOException("Could not read the load profile '"
          + resource
          + "'", e);
    }

  }

  /**
   * The share a gateway sends along one of its flows.
   *
   * @param gatewayId The id of the gateway.
   * @param flowId    The id of the outgoing sequence flow.
   * @return The share, between 0 and 1.
   */
  public double share(
      final String gatewayId,
      final String flowId) {

    final var shares = gateways.get(gatewayId);
    if (shares == null || !shares.containsKey(flowId)) {
      throw new IllegalStateException("The load profile '"
          + source
          + "' has no share for 'gateways."
          + gatewayId
          + "."
          + flowId
          + "'. Add it, between 0 and 1.");
    }
    return shares.get(flowId);

  }

  /**
   * The persons of one user task.
   *
   * @param userTaskId The id of the user task.
   * @return The persons.
   */
  public People peopleOf(
      final String userTaskId) {

    final var found = people.get(userTaskId);
    if (found == null) {
      throw new IllegalStateException("The load profile '"
          + source
          + "' says nothing about 'people."
          + userTaskId
          + "'. Add 'persons', 'think-time' and 'think-time-spread' for it.");
    }
    return found;

  }

  /**
   * The neighbour system whose answer an element waits for.
   *
   * @param catchElementId The id of the element which waits for the answer.
   * @return The neighbour.
   */
  public Neighbour neighbourOf(
      final String catchElementId) {

    final var found = neighbours.get(catchElementId);
    if (found == null) {
      throw new IllegalStateException("The load profile '"
          + source
          + "' says nothing about 'neighbours."
          + catchElementId
          + "'. Add 'name', 'latency', 'latency-spread' and 'no-answer-share' for it.");
    }
    return found;

  }

  /**
   * Turns the YAML tree into a profile, and says precisely what is wrong when it does not fit.
   */
  private record Reader(
                        String source) {

    LoadProfile profile(
        final Map<String, Object> root) {

      if (root == null) {
        throw problem("", "is empty");
      }
      known(root, "", "version", "process", "hot-path", "arrivals", "aggregate", "gateways",
          "people", "neighbours");
      final var version = number(root, "", "version");
      if (version != 1) {
        throw problem("version", "is "
            + version
            + ", and this driver reads version 1");
      }

      final var arrivalsNode = map(root, "", "arrivals");
      known(arrivalsNode, "arrivals.", "pattern", "per-second");
      final var pattern = text(arrivalsNode, "arrivals.", "pattern");
      if (!pattern.equals("random") && !pattern.equals("even")) {
        throw problem("arrivals.pattern", "is '"
            + pattern
            + "', and it has to be 'random' or 'even'");
      }
      final var arrivals = new Arrivals(pattern, positive(arrivalsNode, "arrivals.", "per-second"));

      final var aggregateNode = map(root, "", "aggregate");
      known(aggregateNode, "aggregate.", "size-bytes");
      final var aggregate = new Aggregate((int) number(aggregateNode, "aggregate.", "size-bytes"));

      final var gateways = new LinkedHashMap<String, Map<String, Double>>();
      map(root, "", "gateways").forEach((
          gatewayId,
          flows) -> {
        final var path = "gateways."
            + gatewayId
            + ".";
        final var shares = new LinkedHashMap<String, Double>();
        asMap(flows, path).forEach((
            flowId,
            share) -> shares.put(flowId, share(share, path + flowId)));
        final var sum = shares
            .values()
            .stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        if (Math.abs(sum - 1.0) > 0.001) {
          throw problem("gateways."
              + gatewayId,
              "has shares which add up to "
                  + sum
                  + " instead of 1");
        }
        gateways.put(gatewayId, shares);
      });

      final var people = new LinkedHashMap<String, People>();
      map(root, "", "people").forEach((
          userTaskId,
          node) -> {
        final var path = "people."
            + userTaskId
            + ".";
        final var values = asMap(node, path);
        known(values, path, "persons", "think-time", "think-time-spread");
        people.put(userTaskId, new People(
            (int) positive(values, path, "persons"), duration(values, path, "think-time"), duration(values, path,
                "think-time-spread")));
      });

      final var neighbours = new LinkedHashMap<String, Neighbour>();
      map(root, "", "neighbours").forEach((
          catchElementId,
          node) -> {
        final var path = "neighbours."
            + catchElementId
            + ".";
        final var values = asMap(node, path);
        known(values, path, "name", "latency", "latency-spread", "no-answer-share");
        neighbours.put(catchElementId, new Neighbour(
            text(values, path, "name"), duration(values, path, "latency"), duration(values, path,
                "latency-spread"), share(values.get("no-answer-share"), path
                    + "no-answer-share")));
      });

      final var hotPath = root.get("hot-path");
      if (!(hotPath instanceof List<?> elements) || elements.isEmpty()) {
        throw problem("hot-path", "has to be a list of the BPMN element ids most workflows pass");
      }

      return new LoadProfile(
          source, text(root, "", "process"), elements
              .stream()
              .map(String::valueOf)
              .toList(), arrivals, aggregate, gateways, people, neighbours);

    }

    private void known(
        final Map<String, Object> node,
        final String path,
        final String... keys) {

      final var allowed = List.of(keys);
      node
          .keySet()
          .stream()
          .filter(key -> !allowed.contains(key))
          .findFirst()
          .ifPresent(key -> {
            throw problem(path + key, "is not a key this driver knows. Known here: "
                + allowed);
          });

    }

    private Map<String, Object> map(
        final Map<String, Object> node,
        final String path,
        final String key) {

      return asMap(node.get(key), path + key);

    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(
        final Object value,
        final String path) {

      if (!(value instanceof Map<?, ?>)) {
        throw problem(path, "is missing or not a section with keys below it");
      }
      return (Map<String, Object>) value;

    }

    private String text(
        final Map<String, Object> node,
        final String path,
        final String key) {

      final var value = node.get(key);
      if (value == null || value
          .toString()
          .isBlank()) {
        throw problem(path + key, "is missing");
      }
      return value.toString();

    }

    private double number(
        final Map<String, Object> node,
        final String path,
        final String key) {

      final var value = node.get(key);
      if (!(value instanceof Number number)) {
        throw problem(path + key, "is missing or not a number");
      }
      return number.doubleValue();

    }

    private double positive(
        final Map<String, Object> node,
        final String path,
        final String key) {

      final var value = number(node, path, key);
      if (value <= 0) {
        throw problem(path + key, "is "
            + value
            + ", and it has to be more than 0");
      }
      return value;

    }

    private double share(
        final Object value,
        final String path) {

      if (!(value instanceof Number number) || number.doubleValue() < 0 || number.doubleValue() > 1) {
        throw problem(path, "is '"
            + value
            + "', and it has to be a share between 0 and 1");
      }
      return number.doubleValue();

    }

    private Duration duration(
        final Map<String, Object> node,
        final String path,
        final String key) {

      final var value = text(node, path, key);
      try {
        return Duration.parse(value);
      } catch (final DateTimeParseException e) {
        throw problem(path + key, "is '"
            + value
            + "', and it has to be a duration such as PT2S");
      }

    }

    private IllegalStateException problem(
        final String key,
        final String what) {

      return new IllegalStateException("The load profile '"
          + source
          + "': "
          + (key.isEmpty() ? "the file" : "'"
              + key
              + "'")
          + " "
          + what
          + ".");

    }

  }

}
