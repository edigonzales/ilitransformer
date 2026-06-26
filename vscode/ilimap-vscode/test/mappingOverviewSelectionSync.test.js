const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const distPath = path.join(extensionRoot, 'dist', 'overview', 'mappingOverviewSelectionSync.js');
const { MappingOverviewSelectionSync } = require(distPath);

function summaryWithRules(rules) {
  return {
    available: true,
    message: '',
    mappingName: 'Profile',
    inputCount: 0,
    outputCount: 0,
    ruleCount: rules.length,
    enumMapCount: 0,
    bagCount: 0,
    refCount: 0,
    errorCount: 0,
    warningCount: 0,
    informationCount: 0,
    hintCount: 0,
    inputs: [],
    outputs: [],
    enumMaps: [],
    rules,
    diagnostics: []
  };
}

function rule(id, line, endLine) {
  const location = { line, character: 2 };
  if (typeof endLine === 'number') {
    location.endLine = endLine;
    location.endCharacter = 1;
  }
  return {
    id,
    targetOutput: 'out',
    targetClass: 'M.A',
    sourceCount: 1,
    assignmentCount: 0,
    bagCount: 0,
    refCount: 0,
    status: 'ok',
    location
  };
}

function selectionEvent(uri, line) {
  return {
    textEditor: { document: { uri: { toString() { return uri; } } } },
    selections: [{ active: { line } }]
  };
}

test('findNodeAtPosition matches the rule whose explicit range contains the line', () => {
  const summary = summaryWithRules([rule('r1', 3, 8), rule('r2', 10, 15)]);
  const sync = new MappingOverviewSelectionSync(() => summary, () => {});

  assert.equal(sync.findNodeAtPosition('file:///x.ilimap', { line: 5 }), 'rule:r1');
  assert.equal(sync.findNodeAtPosition('file:///x.ilimap', { line: 12 }), 'rule:r2');
});

test('findNodeAtPosition returns undefined between explicit rule ranges', () => {
  const summary = summaryWithRules([rule('r1', 3, 8), rule('r2', 10, 15)]);
  const sync = new MappingOverviewSelectionSync(() => summary, () => {});

  assert.equal(sync.findNodeAtPosition('file:///x.ilimap', { line: 9 }), undefined);
});

test('findNodeAtPosition falls back to next-rule boundary without explicit end', () => {
  const summary = summaryWithRules([rule('r1', 3), rule('r2', 10)]);
  const sync = new MappingOverviewSelectionSync(() => summary, () => {});

  assert.equal(sync.findNodeAtPosition('file:///x.ilimap', { line: 5 }), 'rule:r1');
  assert.equal(sync.findNodeAtPosition('file:///x.ilimap', { line: 9 }), 'rule:r1');
  assert.equal(sync.findNodeAtPosition('file:///x.ilimap', { line: 10 }), 'rule:r2');
  assert.equal(sync.findNodeAtPosition('file:///x.ilimap', { line: 25 }), 'rule:r2');
});

test('findNodeAtPosition returns undefined without summary, rules, or uri', () => {
  const empty = new MappingOverviewSelectionSync(() => undefined, () => {});
  assert.equal(empty.findNodeAtPosition('file:///x.ilimap', { line: 5 }), undefined);

  const noRules = new MappingOverviewSelectionSync(() => summaryWithRules([]), () => {});
  assert.equal(noRules.findNodeAtPosition('file:///x.ilimap', { line: 5 }), undefined);

  const withRules = new MappingOverviewSelectionSync(() => summaryWithRules([rule('r1', 3, 8)]), () => {});
  assert.equal(withRules.findNodeAtPosition(undefined, { line: 5 }), undefined);
});

test('handleSelectionChange reveals the rule node and deduplicates repeated selections', () => {
  const summary = summaryWithRules([rule('r1', 3, 8), rule('r2', 10, 15)]);
  const revealed = [];
  const sync = new MappingOverviewSelectionSync(() => summary, nodeId => revealed.push(nodeId));

  sync.handleSelectionChange(selectionEvent('file:///x.ilimap', 5));
  sync.handleSelectionChange(selectionEvent('file:///x.ilimap', 6));
  assert.deepEqual(revealed, ['rule:r1']);

  sync.handleSelectionChange(selectionEvent('file:///x.ilimap', 12));
  assert.deepEqual(revealed, ['rule:r1', 'rule:r2']);
});

test('handleSelectionChange ignores selections outside any rule', () => {
  const summary = summaryWithRules([rule('r1', 3, 8)]);
  const revealed = [];
  const sync = new MappingOverviewSelectionSync(() => summary, nodeId => revealed.push(nodeId));

  sync.handleSelectionChange(selectionEvent('file:///x.ilimap', 100));
  assert.deepEqual(revealed, []);
});
