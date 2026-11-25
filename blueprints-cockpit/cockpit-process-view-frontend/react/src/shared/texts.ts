// The texts of the case page. The cockpit hands every web component the language of its user as
// the property 'language'. A language this module does not know gets English.

const TEXTS = {
  en: {
    process: 'Process',
    passed: 'Passed',
    current: 'Current step',
    loading: 'Loading…',
    notFound: 'The workflow module does not know this case.',
    failed: 'The workflow module did not answer. Please try again.',
  },
  de: {
    process: 'Prozess',
    passed: 'Durchlaufen',
    current: 'Aktueller Schritt',
    loading: 'Wird geladen…',
    notFound: 'Das Workflow-Modul kennt diesen Fall nicht.',
    failed: 'Das Workflow-Modul hat nicht geantwortet. Bitte versuchen Sie es noch einmal.',
  },
};

export type Texts = (typeof TEXTS)['en'];

export function textsFor(language: unknown): Texts {
  return typeof language === 'string' && Object.hasOwn(TEXTS, language) ? TEXTS[language as keyof typeof TEXTS] : TEXTS.en;
}
