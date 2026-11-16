// What the other parts of this module take from src/shared, imported as '@shared'. The client of
// the module API is not here: it has an alias of its own, '@gui-client', see src/shared/api.ts.

export { defineInputForm, type InputForm } from './input-form.ts';
export type { Texts } from './texts.ts';

// The single files components in src/shared/input-form build the areas of a form. They are a
// directory of their own, so they take what they share with input-form.ts from here as well.
export { useArea, useCompletion, useFormData, type AreaMessage, type Problem } from './input-form.ts';
