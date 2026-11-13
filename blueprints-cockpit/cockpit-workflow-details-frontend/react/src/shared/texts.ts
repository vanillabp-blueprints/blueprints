// The texts of the case page. The cockpit hands every web component the language of its user as
// the property 'language'. A language this module does not know gets English.

const TEXTS = {
  en: {
    loanRequest: 'Loan request',
    applicant: 'Applicant',
    amount: 'Amount',
    outcome: 'Outcome',
    running: 'Running',
    APPROVED: 'Approved',
    CANCELLED: 'Cancelled',
    openUserTasks: 'Open user tasks',
    noOpenUserTasks: 'No user task is open.',
    cancel: 'Cancel case',
    cancelled: 'The case is cancelled.',
    loading: 'Loading…',
    notFound: 'The workflow module does not know this case.',
    hasEnded: 'This case has ended already.',
    failed: 'The workflow module did not answer. Please try again.',
    userTasksFailed: 'The cockpit did not list the user tasks of this case.',
  },
  de: {
    loanRequest: 'Kreditantrag',
    applicant: 'Antragsteller',
    amount: 'Betrag',
    outcome: 'Ergebnis',
    running: 'Läuft',
    APPROVED: 'Genehmigt',
    CANCELLED: 'Abgebrochen',
    openUserTasks: 'Offene Aufgaben',
    noOpenUserTasks: 'Keine Aufgabe ist offen.',
    cancel: 'Fall abbrechen',
    cancelled: 'Der Fall ist abgebrochen.',
    loading: 'Wird geladen…',
    notFound: 'Das Workflow-Modul kennt diesen Fall nicht.',
    hasEnded: 'Dieser Fall ist schon beendet.',
    failed: 'Das Workflow-Modul hat nicht geantwortet. Bitte versuchen Sie es noch einmal.',
    userTasksFailed: 'Das Cockpit hat die Aufgaben dieses Falls nicht geliefert.',
  },
};

export type Texts = (typeof TEXTS)['en'];

export function textsFor(language: unknown): Texts {
  return typeof language === 'string' && Object.hasOwn(TEXTS, language) ? TEXTS[language as keyof typeof TEXTS] : TEXTS.en;
}
