package blueprint.workflowmodule.loanapproval.model.usertask;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What the form of a user task holds while the task is open: one implementation per kind of
 * user task. It is stored as JSON in a column of {@link Task}, so a new kind of user task needs
 * no new table and no new column.
 *
 * <p>
 * The JSON carries the property {@code taskType}, and its value names the implementation.
 * Jackson reads it to know which class to create. Every implementation has to be listed
 * below, with the name it is stored under. Do not rename a name later: tasks stored before
 * would no longer be read.
 * </p>
 *
 * <p>
 * The implementations are records. A draft is replaced as a whole, never changed in place. So
 * the persistence layer sees a different value and writes it.
 * </p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "taskType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AssessRiskTaskData.class,
        name = "assessRisk"), @JsonSubTypes.Type(value = CheckDocumentTaskData.class, name = "checkDocument")
})
public interface TaskData {
}
