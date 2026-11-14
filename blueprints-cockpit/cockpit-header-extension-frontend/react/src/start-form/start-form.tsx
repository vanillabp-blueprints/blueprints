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

import { useState, type FormEvent } from 'react';
import type { BcElementProps } from '@vanillabp/bc-module-kit';
import { ApiError, loanApprovalApi, type StartedLoanRequest } from '../shared/api.ts';
import { defineReactElement } from '../shared/react-element.tsx';
import { STYLE } from '../shared/style.ts';
import { textsFor } from '../shared/texts.ts';
import { START_FORM, START_FORM_ELEMENTS } from '../loan-approval.ts';

/**
 * The button in the header. It opens the start form in a dialog of the cockpit and waits until
 * the user closes it. A form closed with OK hands over the new loan request, and the button says
 * so in a notification. A form closed with "Cancel" hands over nothing.
 */
function RequestLoanButton(props: BcElementProps) {
  const texts = textsFor(props.language);
  const [open, setOpen] = useState(false);
  const requestLoan = async () => {
    setOpen(true);
    try {
      const started = (await props.openDialog({ entry: START_FORM, element: START_FORM_ELEMENTS.form })) as StartedLoanRequest | undefined;
      if (started?.loanRequestId) {
        // A cockpit which cannot show notifications refuses. The case is in the list anyway.
        props.showNotification({ level: 'success', text: `${texts.started} ${started.loanRequestId}` }).catch(() => {});
      }
    } finally {
      setOpen(false);
    }
  };
  return (
    <>
      <style>{STYLE}</style>
      <button type="button" data-testid="request-loan" disabled={open} onClick={requestLoan}>
        {texts.requestLoan}
      </button>
    </>
  );
}

type Problem = 'invalid' | 'refused' | 'failed';

/**
 * The start form. OK sends what the user typed to the module and closes the dialog with the id of
 * the new loan request. The module refuses an empty applicant and an amount which is not
 * positive, and the form checks the same before it sends anything. A refused or failed request
 * keeps the dialog open, so the user can correct the values or try again.
 */
function StartForm(props: BcElementProps) {
  const texts = textsFor(props.language);
  const [applicant, setApplicant] = useState('');
  const [amount, setAmount] = useState('');
  const [sending, setSending] = useState(false);
  const [problem, setProblem] = useState<Problem>();

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const amountAsNumber = Number(amount);
    if (applicant.trim() === '' || !Number.isInteger(amountAsNumber) || amountAsNumber < 1) {
      setProblem('invalid');
      return;
    }
    setSending(true);
    setProblem(undefined);
    try {
      const started = await loanApprovalApi.requestLoan({ applicant: applicant.trim(), amount: amountAsNumber });
      await props.closeDialog(started);
    } catch (error) {
      setProblem(error instanceof ApiError && error.status === 400 ? 'refused' : 'failed');
      setSending(false);
    }
  };

  return (
    <>
      <style>{STYLE}</style>
      <form data-testid="start-form" onSubmit={submit} noValidate>
        <h2>{texts.title}</h2>
        <label>
          {texts.applicant}
          <input data-testid="start-form-applicant" value={applicant} onChange={(event) => setApplicant(event.target.value)} autoFocus />
        </label>
        <label>
          {texts.amount}
          <input data-testid="start-form-amount" type="number" min="1" step="1" inputMode="numeric" value={amount} onChange={(event) => setAmount(event.target.value)} />
        </label>
        {problem ? <p className="problem" data-testid="start-form-error">{texts[problem]}</p> : null}
        <div className="buttons">
          <button type="button" className="secondary" data-testid="start-form-cancel" disabled={sending} onClick={() => props.closeDialog()}>
            {texts.cancel}
          </button>
          <button type="submit" data-testid="start-form-ok" disabled={sending}>
            {sending ? texts.sending : texts.ok}
          </button>
        </div>
      </form>
    </>
  );
}

defineReactElement(START_FORM_ELEMENTS.button, RequestLoanButton);
defineReactElement(START_FORM_ELEMENTS.form, StartForm);
