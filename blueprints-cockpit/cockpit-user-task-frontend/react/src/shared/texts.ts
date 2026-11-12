// The texts of the forms. The cockpit hands every web component the language of its user as the
// property 'language'. A language this module does not know gets English.

const TEXTS = {
  en: {
    loanRequest: 'Loan request',
    amount: 'Amount',
    riskAssessment: 'Risk assessment',
    documentsNote: 'Note on the documents',
    complete: 'Complete',
    completed: 'Completed',
    loading: 'Loading…',
    notFound: 'This user task is not open any more.',
    failed: 'The workflow module did not answer. Please try again.',
  },
  de: {
    loanRequest: 'Kreditantrag',
    amount: 'Betrag',
    riskAssessment: 'Risikobewertung',
    documentsNote: 'Notiz zu den Unterlagen',
    complete: 'Abschließen',
    completed: 'Abgeschlossen',
    loading: 'Wird geladen…',
    notFound: 'Diese Aufgabe ist nicht mehr offen.',
    failed: 'Das Workflow-Modul hat nicht geantwortet. Bitte versuchen Sie es noch einmal.',
  },
};

export type Texts = (typeof TEXTS)['en'];

export function textsFor(language: unknown): Texts {
  return typeof language === 'string' && Object.hasOwn(TEXTS, language) ? TEXTS[language as keyof typeof TEXTS] : TEXTS.en;
}
