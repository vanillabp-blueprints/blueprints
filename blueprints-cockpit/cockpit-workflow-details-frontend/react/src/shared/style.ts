// The styles of the case page. Each web component puts them into its own shadow root. So they
// never reach the cockpit, and the styles of the cockpit never reach the page.
//
// Long content must not break the page. A long word wraps instead of widening its area, and a
// long list scrolls in the area the cockpit gives it. The cockpit keeps the column with the
// information and the actions in place, so the button never scrolls away.

export const STYLE = `
:host { display: block; min-width: 0; font-family: system-ui, sans-serif; font-size: 1rem; color: #1d2733; overflow-wrap: anywhere; }
h2 { font-size: 1.125rem; margin: 0 0 0.75rem; }
dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.25rem 1rem; margin: 0 0 1rem; }
dt { font-weight: 600; }
dd { margin: 0; white-space: pre-wrap; }
ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
li { padding: 0.5rem 0.75rem; border: 1px solid #c9d1d9; border-radius: 0.25rem; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem; }
button { font: inherit; padding: 0.5rem 1.25rem; border: 0; border-radius: 0.25rem; background: #b3261e; color: #fff; cursor: pointer; }
button:disabled { background: #b6c0ca; cursor: default; }
.problem { color: #b3261e; }
`;
