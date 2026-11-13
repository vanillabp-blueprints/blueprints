package blueprint.workflowmodule.loanapproval.model.usertask;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores {@link TaskData} as JSON text. A converter of the JPA standard, so it works with
 * every database and needs no JSON type of the database or of Hibernate.
 *
 * <p>
 * The type of the data is part of the JSON (see {@link TaskData}). So reading a column gives
 * back the record of the right kind of user task.
 * </p>
 */
@Converter
public class TaskDataConverter implements AttributeConverter<TaskData, String> {

  private static final JsonMapper JSON = JsonMapper
      .builder()
      .build();

  @Override
  public String convertToDatabaseColumn(
      final TaskData data) {

    return data == null ? null : JSON.writeValueAsString(data);

  }

  @Override
  public TaskData convertToEntityAttribute(
      final String json) {

    return json == null ? null : JSON.readValue(json, TaskData.class);

  }

}
