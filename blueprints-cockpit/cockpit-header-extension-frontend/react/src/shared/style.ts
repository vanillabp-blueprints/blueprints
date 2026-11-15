// The styles of the button and the start form. Each web component puts them into its own shadow
// root. So they never reach the cockpit, and the styles of the cockpit never reach the module.
//
// The button sits in the header of the cockpit, next to the buttons of the cockpit and of other
// modules. So it stays one line high and never grows with its text. The form takes the width the
// dialog of the cockpit gives it, and a long word wraps instead of widening the dialog.

export const STYLE = `
:host { display: block; min-width: 0; font-family: system-ui, sans-serif; font-size: 1rem; color: #1d2733; overflow-wrap: anywhere; }
:host([hidden]) { display: none; }
h2 { font-size: 1.125rem; margin: 0 0 1rem; }
form { display: grid; gap: 0.75rem; min-width: min(24rem, 100%); }
label { display: grid; gap: 0.25rem; font-weight: 600; }
input { font: inherit; padding: 0.375rem 0.5rem; border: 1px solid #8c99a6; border-radius: 0.25rem; min-width: 0; }
.buttons { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.5rem; }
button { font: inherit; padding: 0.375rem 1rem; border: 0; border-radius: 0.25rem; background: #1f5fa6; color: #fff; cursor: pointer; white-space: nowrap; }
button.secondary { background: #e3e8ee; color: #1d2733; }
button:disabled { background: #b6c0ca; cursor: default; }
.problem { color: #b3261e; margin: 0; }
`;
