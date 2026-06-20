const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const manifest = JSON.parse(fs.readFileSync(path.join(extensionRoot, 'package.json'), 'utf8'));

test('registers the ilimap language for .ilimap files', () => {
  const languages = manifest.contributes.languages;
  const ilimap = languages.find((language) => language.id === 'ilimap');

  assert.ok(ilimap);
  assert.deepEqual(ilimap.extensions, ['.ilimap']);
  assert.equal(ilimap.configuration, './language-configuration.json');
});

test('contributes the ilimap TextMate grammar', () => {
  const grammars = manifest.contributes.grammars;
  const grammar = grammars.find((candidate) => candidate.language === 'ilimap');

  assert.ok(grammar);
  assert.equal(grammar.scopeName, 'source.ilimap');
  assert.equal(grammar.path, './syntaxes/ilimap.tmLanguage.json');
});

test('registers public commands', () => {
  const commands = manifest.contributes.commands.map((command) => command.command);

  assert.ok(commands.includes('ilimap.restartLanguageServer'));
  assert.ok(commands.includes('ilimap.validate'));
});

test('declares required settings', () => {
  const properties = manifest.contributes.configuration.properties;

  assert.equal(properties['ilimap.java.path'].default, 'java');
  assert.equal(properties['ilimap.server.jar'].default, '');
});

test('does not declare client-side ilimap semantics settings', () => {
  const propertyNames = Object.keys(manifest.contributes.configuration.properties);

  assert.deepEqual(propertyNames.sort(), ['ilimap.java.path', 'ilimap.server.jar']);
});
