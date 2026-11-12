// The styles of the forms. Each web component puts them into its own shadow root. So they never
// reach the cockpit, and the styles of the cockpit never reach the forms.

export const STYLE = `
:host { display: block; font-family: system-ui, sans-serif; font-size: 1rem; color: #1d2733; }
.field { display: flex; flex-direction: column; gap: 0.375rem; }
label { font-weight: 600; }
textarea { font: inherit; min-height: 5rem; padding: 0.5rem; border: 1px solid #8a96a3; border-radius: 0.25rem; }
textarea:focus { outline: 2px solid #2f6fbd; outline-offset: 1px; }
dl { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 1rem; margin: 0 0 1rem; }
dt { font-weight: 600; }
dd { margin: 0; white-space: pre-wrap; }
.actions { display: flex; align-items: center; gap: 1rem; margin-top: 1rem; }
button { font: inherit; padding: 0.5rem 1.25rem; border: 0; border-radius: 0.25rem; background: #2f6fbd; color: #fff; cursor: pointer; }
button:disabled { background: #b6c0ca; cursor: default; }
.problem { color: #b3261e; }
`;
