package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case for people: GET requests only, so a loan approval can be started
 * and looked at in a browser. The forms of the user tasks have endpoints of their own, in
 * {@link UserTaskFormController}.
 *
 * <p>
 * It talks to {@link Service} and to nothing else. That the use case happens to be
 * implemented by a BPMN process is none of its business.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/loan-approval")
public class ApiController {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL to remember. The URLs of the forms are logged
   * once the user tasks are open.
   *
   * @param amount The amount requested.
   * @return The id of the loan request started.
   */
  @GetMapping("/start")
  public String start(
      @RequestParam(defaultValue = "5000") final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, amount);

    log.info(
        "Show the result -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * Shows what the process did: the aggregate, with every user task and its data.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @GetMapping("/{loanRequestId}")
  public String show(
      @PathVariable final String loanRequestId) {

    return loanApproval
        .describe(loanRequestId)
        .orElse("unknown loan request '"
            + loanRequestId
            + "'");

  }

}
