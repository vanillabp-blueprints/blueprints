// `npm run start` will start the dev shell with the micro-frontends of this project. The dev shell
// comes from the cockpits generated with the skill business-cockpit-user-interface, as the
// package @acme/bc-dev-shell-react. None of them publishes it yet. Until one does, this script
// only says so. See the README, section "Running it".

console.error([
  'npm run start: the dev shell is not part of this blueprint yet.',
  'It comes with the package @acme/bc-dev-shell-react of a generated cockpit, as soon as one publishes it.',
  'Until then: `npm run build && npm test` shows the micro-frontends in the host of the module kit.',
].join('\n'));
process.exit(1);
