package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.api.model.AssessRiskInput;
import blueprint.workflowmodule.loanapproval.config.Permission;
import blueprint.workflowmodule.loanapproval.model.usertask.AssessRiskTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.CheckDocumentTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.TaskData;
import io.vanillabp.cockpit.support.usertask.BcUnknownTaskException;

/**
 * Who may call the endpoints of a form. The test calls {@link UserTaskFormController} as a
 * bean, as a user with given authorities. So it proves the check of the module itself, without
 * the login of an application in front of it.
 */
public class UserTaskFormSecurityIT extends WorkflowModuleTest {

  @Autowired
  private UserTaskFormController forms;

  @Autowired
  private Service loanApproval;

  @Autowired
  private StoredLoanApprovals stored;

  @Test
  @WithMockUser(username = "risk-officer", authorities = Permission.ASSESS_RISK)
  public void theRightAuthorityReadsSavesAndCompletes() {

    final var loanRequestId = requestLoanAndWaitForTheTasks();
    final var riskAssessment = taskId(loanRequestId, AssessRiskTaskData.class);

    forms.saveAssessRiskForm(loanRequestId, riskAssessment, new AssessRiskInput(true, "draft"));
    assertThat(forms.getAssessRiskForm(loanRequestId, riskAssessment).comment()).isEqualTo("draft");

    forms.completeAssessRisk(loanRequestId, riskAssessment, new AssessRiskInput(true, "stable income"));

    // The user who completed the task is the one logged in, not a value of the request.
    final var form = forms.getAssessRiskForm(loanRequestId, riskAssessment);
    assertThat(form.task().completedBy()).isEqualTo("risk-officer");
    assertThat(form.task().open()).isFalse();

  }

  @Test
  @WithMockUser(username = "clerk", authorities = Permission.CHECK_DOCUMENT)
  public void anotherAuthorityIsRefused() {

    final var loanRequestId = requestLoanAndWaitForTheTasks();
    final var riskAssessment = taskId(loanRequestId, AssessRiskTaskData.class);

    assertThatThrownBy(() -> forms.getAssessRiskForm(loanRequestId, riskAssessment))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> forms.saveAssessRiskForm(loanRequestId, riskAssessment, new AssessRiskInput(false, "")))
        .isInstanceOf(AccessDeniedException.class);

    // The clerk may read a document check, and it shows no risk assessment yet.
    final var documentCheck = forms.getCheckDocumentForm(
        loanRequestId, taskId(loanRequestId, CheckDocumentTaskData.class));
    assertThat(documentCheck.riskAcceptable()).isNull();
    // But the id of a risk assessment, sent to the endpoint of a document check, is unknown
    // there. The authority of an endpoint only covers tasks of its own kind.
    assertThatThrownBy(() -> forms.getCheckDocumentForm(loanRequestId, riskAssessment))
        .isInstanceOf(BcUnknownTaskException.class);

  }

  @Test
  public void nobodyLoggedInIsRefused() {

    final var loanRequestId = requestLoanAndWaitForTheTasks();

    assertThatThrownBy(() -> forms.getAssessRiskForm(
        loanRequestId, taskId(loanRequestId, AssessRiskTaskData.class)))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

  }

  private String requestLoanAndWaitForTheTasks() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    awaitAggregate(
        stored::find,
        loanRequestId,
        aggregate -> aggregate.getTasks().size() == 3);

    return loanRequestId;

  }

  private String taskId(
      final String loanRequestId,
      final Class<? extends TaskData> kind) {

    return stored
        .find(loanRequestId)
        .orElseThrow()
        .getTasks()
        .values()
        .stream()
        .filter(task -> task.holds(kind))
        .findFirst()
        .orElseThrow()
        .getTaskId();

  }

}
