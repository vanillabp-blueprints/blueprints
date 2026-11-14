// The texts of the button and the start form. The cockpit hands every web component the language
// of its user as the property 'language'. A language this module does not know gets English.

const TEXTS = {
  en: {
    requestLoan: 'Request a loan',
    title: 'Request a loan',
    applicant: 'Applicant',
    amount: 'Amount',
    ok: 'OK',
    cancel: 'Cancel',
    sending: 'Sending…',
    started: 'The loan request is started. Its id is',
    invalid: 'Please enter the applicant and an amount above zero.',
    refused: 'The workflow module did not accept the loan request.',
    failed: 'The workflow module did not answer. Please try again.',
  },
  de: {
    requestLoan: 'Kredit beantragen',
    title: 'Kredit beantragen',
    applicant: 'Antragsteller',
    amount: 'Betrag',
    ok: 'OK',
    cancel: 'Abbrechen',
    sending: 'Wird gesendet…',
    started: 'Der Kreditantrag ist gestartet. Seine Nummer ist',
    invalid: 'Bitte geben Sie den Antragsteller und einen Betrag über null ein.',
    refused: 'Das Workflow-Modul hat den Kreditantrag nicht angenommen.',
    failed: 'Das Workflow-Modul hat nicht geantwortet. Bitte versuchen Sie es noch einmal.',
  },
};

export type Texts = (typeof TEXTS)['en'];

export function textsFor(language: unknown): Texts {
  return typeof language === 'string' && Object.hasOwn(TEXTS, language) ? TEXTS[language as keyof typeof TEXTS] : TEXTS.en;
}
