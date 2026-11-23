// Data entry Header: the buttons this module puts into the header of the cockpit. Plain
// JavaScript, it imports no framework. Header is an entry of the module's own, not one of the
// four every cockpit knows. So the build names it for bc-check-entries with --data-entry Header.
//
// A cockpit with a header asks this entry of every module the user may see. Which modules that
// are, the cockpit says: GET /gui/api/v1/workflow-module lists only the modules whose groups the
// user is in. The backend names the access group LOAN_APPROVAL_USERS. Seeing the module is not
// enough for the button, though: it is for loan officers only. So this function checks the
// feature role in the groups of the user signed in. The cockpit has already completed these
// groups with the group hierarchies of the modules (GET /gui/api/v1/app/current-user).
//
// The check decides what the user sees, not what the user may do. The backend has to refuse a
// request of somebody who is no loan officer on its own.
//
// A cockpit without a header never asks this entry and never loads it. The module still works
// there, only without the button.

import type { BcElementRef, GuiApiData } from '@vanillabp/bc-module-kit';
import { LOAN_OFFICERS, START_FORM, START_FORM_ELEMENTS } from '@loan-approval';

/** A group of the user signed in, as the GUI API of the cockpit answers it. */
type Group = { id: string };

const isLoanOfficer = (currentUser: GuiApiData): boolean =>
  ((currentUser.groups ?? []) as Group[]).some((group) => group?.id === LOAN_OFFICERS);

/** undefined: no button for this user, because nobody is logged in or the user is no loan officer. */
export const header = (currentUser: GuiApiData | undefined): BcElementRef[] | undefined => {
  if (!currentUser?.id) return undefined;
  if (!isLoanOfficer(currentUser)) return undefined;
  return [{ entry: START_FORM, element: START_FORM_ELEMENTS.button }];
};
