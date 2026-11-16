// The texts of the forms. The cockpit hands every web component the language of its user as the
// property 'language'. A language this module does not know gets English.

const TEXTS = {
  en: {
    loanRequest: 'Loan request',
    amount: 'Amount',
    creditScore: 'Credit score',
    riskAssessed: 'Risk assessed',
    approve: 'Approve the contract',
    completed: 'Completed',
    notSigned: 'The customer has not signed the contract yet. Please try again later.',
    failed: 'The workflow module did not answer. Please try again.',
  },
  de: {
    loanRequest: 'Kreditantrag',
    amount: 'Betrag',
    creditScore: 'Bonität',
    riskAssessed: 'Risiko bewertet',
    approve: 'Vertrag genehmigen',
    completed: 'Abgeschlossen',
    notSigned: 'Der Kunde hat den Vertrag noch nicht unterschrieben. Bitte versuchen Sie es später noch einmal.',
    failed: 'Das Workflow-Modul hat nicht geantwortet. Bitte versuchen Sie es noch einmal.',
  },
};

export type Texts = (typeof TEXTS)['en'];

export function textsFor(language: unknown): Texts {
  return typeof language === 'string' && Object.hasOwn(TEXTS, language) ? TEXTS[language as keyof typeof TEXTS] : TEXTS.en;
}
