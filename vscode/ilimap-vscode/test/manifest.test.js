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
  assert.ok(commands.includes('ilimap.showLanguageServerLogs'));
  assert.ok(commands.includes('ilimap.format'));
  assert.ok(commands.includes('ilimap.validate'));
  assert.ok(commands.includes('ilimap.openMappingOverview'));
  assert.ok(commands.includes('ilimap.showRuleInOverview'));
  assert.ok(commands.includes('ilimap.showRuleCoverage'));
  assert.ok(commands.includes('ilimap.mappingExplorer.refresh'));
  assert.ok(commands.includes('ilimap.mappingExplorer.revealInEditor'));
  assert.ok(commands.includes('ilimap.mappingExplorer.showInOverview'));
});

test('activates mapping overview command', () => {
  assert.ok(manifest.activationEvents.includes('onCommand:ilimap.openMappingOverview'));
});

test('contributes and activates rule code lens commands', () => {
  const commands = manifest.contributes.commands;
  const showInOverview = commands.find((command) => command.command === 'ilimap.showRuleInOverview');
  const showCoverage = commands.find((command) => command.command === 'ilimap.showRuleCoverage');

  assert.ok(showInOverview);
  assert.equal(showInOverview.title, 'ilimap: Show Rule in Mapping Overview');
  assert.ok(showCoverage);
  assert.equal(showCoverage.title, 'ilimap: Show Rule Coverage');

  assert.ok(manifest.activationEvents.includes('onCommand:ilimap.showRuleInOverview'));
  assert.ok(manifest.activationEvents.includes('onCommand:ilimap.showRuleCoverage'));
});

test('contributes ilimap view container and mapping explorer view', () => {
  const viewContainers = manifest.contributes.viewsContainers;
  assert.ok(viewContainers);
  assert.ok(viewContainers.activitybar);
  const container = viewContainers.activitybar.find(c => c.id === 'ilimap');
  assert.ok(container);
  assert.equal(container.title, 'ilimap');
  assert.equal(container.icon, 'images/icon.png');

  const views = manifest.contributes.views;
  assert.ok(views);
  assert.ok(views.ilimap);
  const explorerView = views.ilimap.find(v => v.id === 'ilimap.mappingExplorer');
  assert.ok(explorerView);
  assert.equal(explorerView.name, 'Mapping Explorer');
});

test('activates on view and explorer commands', () => {
  assert.ok(manifest.activationEvents.includes('onView:ilimap.mappingExplorer'));
  assert.ok(manifest.activationEvents.includes('onCommand:ilimap.mappingExplorer.refresh'));
  assert.ok(manifest.activationEvents.includes('onCommand:ilimap.mappingExplorer.revealInEditor'));
  assert.ok(manifest.activationEvents.includes('onCommand:ilimap.mappingExplorer.showInOverview'));
});

test('declares required settings', () => {
  const properties = manifest.contributes.configuration.properties;

  assert.equal(properties['ilimap.java.path'].default, '');
  assert.equal(properties['ilimap.server.jarPath'].default, '');
  assert.deepEqual(properties['ilimap.server.jvmArgs'].default, []);
  assert.equal(properties['ilimap.server.restartOnJarChange'], undefined);
});

test('declares build, packaging, and publishing scripts', () => {
  assert.equal(manifest.scripts['vscode:prepublish'], 'npm run build');
  assert.equal(manifest.scripts.compile, 'npm run build');
  assert.equal(manifest.scripts.build, 'tsc -p ./');
  assert.equal(manifest.scripts.watch, 'tsc -watch -p ./');
  assert.equal(manifest.scripts['version:ci'], 'node ./scripts/set-ci-version.js');
  assert.equal(manifest.scripts['package:vsix'], 'vsce package --out ilimap-vscode.vsix');
  assert.equal(manifest.scripts['check:vsix'], 'node ./scripts/assert-vsix-contents.js ilimap-vscode.vsix');
  assert.equal(manifest.scripts['publish:vsmarketplace'], 'vsce publish --packagePath ilimap-vscode.vsix');
  assert.equal(manifest.scripts['publish:openvsx'], 'ovsx publish ilimap-vscode.vsix');
});

test('does not declare client-side ilimap semantics settings', () => {
  const propertyNames = Object.keys(manifest.contributes.configuration.properties);

  assert.deepEqual(propertyNames.sort(), [
    'ilimap.java.path',
    'ilimap.server.jarPath',
    'ilimap.server.jvmArgs'
  ]);
});

test('declares marketplace metadata', () => {
  assert.equal(manifest.version, '0.1.0');
  assert.equal(manifest.license, 'MIT');
  assert.equal(manifest.icon, 'images/icon.png');
  assert.deepEqual(manifest.galleryBanner, {
    color: '#f4efe7',
    theme: 'light'
  });
  assert.equal(manifest.repository.url, 'https://github.com/edigonzales/ilitransformer.git');
  assert.equal(manifest.bugs.url, 'https://github.com/edigonzales/ilitransformer/issues');
  assert.ok(manifest.keywords.includes('ilimap'));
  assert.ok(manifest.keywords.includes('lsp'));
});
