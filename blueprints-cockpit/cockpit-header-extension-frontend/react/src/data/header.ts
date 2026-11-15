// Data entry Header: the buttons this module puts into the header of the cockpit. Plain
// JavaScript, it imports no framework. Header is an entry of the module's own, not one of the
// four every cockpit knows. So the build names it for bc-check-entries with --data-entry Header.
//
// A cockpit with a header asks this entry of every module the user may see. Which modules that
// are, the cockpit says: GET /gui/api/v1/workflow-module lists only the modules whose groups the
// user is in. The backend names the group. So this function does not check any rights itself.
//
// A cockpit without a header never asks this entry and never loads it. The module still works
// there, only without the button.

import type { BcElementRef, GuiApiData } from '@vanillabp/bc-module-kit';
import { START_FORM, START_FORM_ELEMENTS } from '../loan-approval.ts';

/** undefined: no button for this user. Here only while nobody is logged in. */
export const header = (currentUser: GuiApiData | undefined): BcElementRef[] | undefined => {
  if (!currentUser?.id) return undefined;
  return [{ entry: START_FORM, element: START_FORM_ELEMENTS.button }];
};
