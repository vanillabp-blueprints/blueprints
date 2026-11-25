// What the other parts of this module take from src/shared, imported as '@shared'. The client of
// the module API is not here: it has an alias of its own, '@gui-client', see src/shared/api.ts.

export { loadProcess, loanRequestIdOf, problemOf, type CaseProcess, type Problem } from './case-process.ts';
export { defineReactElement } from './react-element.tsx';
export { STYLE } from './style.ts';
export { textsFor, type Texts } from './texts.ts';
