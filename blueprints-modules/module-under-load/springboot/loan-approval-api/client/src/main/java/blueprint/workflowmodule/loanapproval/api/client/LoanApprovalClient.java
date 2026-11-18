package blueprint.workflowmodule.loanapproval.api.client;

/**
 * The client of the API of the application under load, as this module imports it. A type of its own, so an application
 * which imports {@link LoanApprovalApi} into an HTTP service group of its own, for example with other
 * keys for a second workflow module, gets no second bean of this type.
 */
public interface LoanApprovalClient extends LoanApprovalApi {
}
