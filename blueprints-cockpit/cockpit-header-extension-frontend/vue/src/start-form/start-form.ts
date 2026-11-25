// Visual entry StartForm: the button "Request a loan" for the header of the cockpit, and the start
// form it opens. It exports nothing. When the browser evaluates it, it defines the two web
// components:
//
//   button  the button in the header. The data entry Header names it
//   form    the start form. The button asks the cockpit to show it in a modal dialog
//
// A loan request which does not exist yet has no user task and no case page, so there is no other
// place for this form. The button opens it with openDialog(), and the cockpit draws the form above
// everything. The form calls the module API, and the module starts the process. Then the form
// closes the dialog with closeDialog() and hands over the id of the new loan request, and the
// promise of openDialog() resolves with it. The cockpit learns about the new case from the module,
// like about every other case, and lists it.
//
// The entry imports its own components and nothing else of the module's user interface. So the
// cockpit loads them only when it draws its header. A dev shell which draws every part of the
// module keeps a list of names and components of its own, which no visual entry imports.

import { defineVueElement } from '@shared';
import { START_FORM_ELEMENTS } from '@loan-approval';
import RequestLoanButton from './RequestLoanButton.ce.vue';
import StartForm from './StartForm.ce.vue';

defineVueElement(START_FORM_ELEMENTS.button, RequestLoanButton);
defineVueElement(START_FORM_ELEMENTS.form, StartForm);
