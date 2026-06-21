const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const { validateVsixEntries } = require(path.join(extensionRoot, 'scripts', 'vsix-contents.js'));

test('validateVsixEntries accepts a package with the expected runtime and server assets', () => {
  const result = validateVsixEntries([
    'extension/readme.md',
    'extension/changelog.md',
    'extension/LICENSE.txt',
    'extension/images/icon.png',
    'extension/dist/extension.js',
    'extension/server/ilimap-lsp-all.jar',
    'extension/server/jre/linux-x64/bin/java'
  ]);

  assert.deepEqual(result, {
    missing: [],
    forbidden: []
  });
});

test('validateVsixEntries reports missing required files and forbidden dev files', () => {
  const result = validateVsixEntries([
    'extension/readme.md',
    'extension/images/icon-source.png',
    'extension/scripts/set-ci-version.js',
    'extension/src/client.ts',
    'extension/dist/extension.js.map'
  ]);

  assert.deepEqual(result.missing.sort(), [
    'extension/LICENSE.txt',
    'extension/changelog.md',
    'extension/images/icon.png',
    'extension/server/ilimap-lsp-all.jar',
    'extension/server/jre/'
  ]);
  assert.deepEqual(result.forbidden.sort(), [
    'extension/dist/extension.js.map',
    'extension/images/icon-source.png',
    'extension/scripts/set-ci-version.js',
    'extension/src/client.ts'
  ]);
});
