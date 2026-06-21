const REQUIRED_EXACT = [
  'extension/readme.md',
  'extension/changelog.md',
  'extension/LICENSE.txt',
  'extension/images/icon.png',
  'extension/server/ilimap-lsp-all.jar'
];

const REQUIRED_PREFIXES = ['extension/dist/', 'extension/server/jre/'];

const FORBIDDEN_PREFIXES = [
  'extension/src/',
  'extension/test/',
  'extension/.vscode/',
  'extension/scripts/',
  'extension/images/icon-source.png'
];

const FORBIDDEN_SUFFIXES = ['.map'];

function validateVsixEntries(entries) {
  const normalized = entries.filter(Boolean);

  const missing = [
    ...REQUIRED_EXACT.filter((entry) => !normalized.includes(entry)),
    ...REQUIRED_PREFIXES.filter((prefix) => !normalized.some((entry) => entry.startsWith(prefix)))
  ];

  const forbidden = normalized.filter(
    (entry) =>
      FORBIDDEN_PREFIXES.some((prefix) => entry.startsWith(prefix)) ||
      FORBIDDEN_SUFFIXES.some((suffix) => entry.endsWith(suffix))
  );

  return {
    missing,
    forbidden
  };
}

module.exports = {
  validateVsixEntries
};
