const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const { renderMappingOverviewHtml } = require(path.join(
  extensionRoot,
  'dist',
  'webview',
  'mappingOverviewHtml.js'
));

test('renders strict CSP without scripts or editable controls', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.match(html, /Content-Security-Policy" content="default-src 'none'; style-src 'nonce-test-nonce';"/);
  assert.doesNotMatch(html, /<script\b/i);
  assert.doesNotMatch(html, /<input\b/i);
  assert.doesNotMatch(html, /<button\b/i);
  assert.doesNotMatch(html, /<form\b/i);
  assert.doesNotMatch(html, /contenteditable/i);
});

test('escapes user-provided labels and values', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.doesNotMatch(html, /<img\b/i);
  assert.doesNotMatch(html, /<svg\b/i);
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/);
  assert.match(html, /&lt;svg onload=alert\(2\)&gt;/);
});

function summary() {
  return {
    available: true,
    message: '',
    mappingName: '<img src=x onerror=alert(1)>',
    inputCount: 1,
    outputCount: 1,
    ruleCount: 1,
    enumMapCount: 1,
    bagCount: 0,
    refCount: 0,
    errorCount: 0,
    warningCount: 1,
    informationCount: 0,
    hintCount: 0,
    inputs: [
      {
        id: 'src',
        path: '<svg onload=alert(2)>',
        model: 'M',
        format: 'xtf'
      }
    ],
    outputs: [{ id: 'out', path: 'out.xtf', model: 'M', format: 'xtf' }],
    enumMaps: [{ id: 'Quality', entryCount: 2 }],
    rules: [
      {
        id: 'r1',
        targetOutput: 'out',
        targetClass: 'M.A',
        sourceCount: 1,
        assignmentCount: 1,
        bagCount: 0,
        refCount: 0,
        status: 'warning'
      }
    ],
    diagnostics: [{ code: 'CODE', severity: 'warning', message: 'warn', line: 0, character: 0 }]
  };
}
