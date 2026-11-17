package blueprint.workflowmodule.loanapproval.model.usertask;

import java.io.UncheckedIOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link TaskData} as JSON text. A converter of the JPA standard, so it works with
 * every database and needs no JSON type of the database or of Hibernate.
 *
 * <p>
 * The type of the data is part of the JSON (see {@link TaskData}). So reading a column gives
 * back the record of the right kind of user task.
 * </p>
 *
 * <p>
 * Quarkus brings Jackson 2. It needs the module for the types of {@code java.time}, and it is
 * told to write a date as text, as the other platform does. Jackson 2 also says that writing and
 * reading may fail, so the two methods turn its exception into an unchecked one.
 * </p>
 */
@Converter
public class TaskDataConverter implements AttributeConverter<TaskData, String> {

  private static final JsonMapper JSON = JsonMapper
      .builder()
      .addModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .build();

  @Override
  public String convertToDatabaseColumn(
      final TaskData data) {

    try {
      return data == null ? null : JSON.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }

  }

  @Override
  public TaskData convertToEntityAttribute(
      final String json) {

    try {
      return json == null ? null : JSON.readValue(json, TaskData.class);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }

  }

}
