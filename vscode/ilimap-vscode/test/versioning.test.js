const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const { applyPublishVersion, derivePublishVersion } = require(path.join(
  extensionRoot,
  'scripts',
  'versioning.js'
));

test('derivePublishVersion replaces the patch component with the CI run number', () => {
  assert.equal(derivePublishVersion('0.1.0', 42), '0.1.42');
});

test('derivePublishVersion rejects invalid base versions', () => {
  assert.throws(() => derivePublishVersion('0.1', 42), /semantic base version/);
});

test('applyPublishVersion updates package.json and package-lock versions together', () => {
  const packageJson = { version: '0.1.0' };
  const packageLock = {
    version: '0.1.0',
    packages: {
      '': {
        version: '0.1.0'
      }
    }
  };

  applyPublishVersion(packageJson, packageLock, '0.1.77');

  assert.equal(packageJson.version, '0.1.77');
  assert.equal(packageLock.version, '0.1.77');
  assert.equal(packageLock.packages[''].version, '0.1.77');
});
