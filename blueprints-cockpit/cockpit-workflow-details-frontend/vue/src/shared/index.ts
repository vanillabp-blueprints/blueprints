// What the other parts of this module take from src/shared, imported as '@shared'. The client of
// the module API is not here: it has an alias of its own, '@gui-client', see src/shared/api.ts.

export { defineVueElement, useBcElement } from './vue-element.ts';
export { STYLE } from './style.ts';
export { textsFor, type Texts } from './texts.ts';

// What the single file components of the case page share. They are in a directory of their own,
// src/pages/loan-approval-page, so they take it from here.
export {
  forgetCase,
  useCase,
  useCasePage,
  useOpenUserTasks,
  useReloadOnMessage,
  problemOf,
  type AreaMessage,
  type Problem,
} from './case-page.ts';
