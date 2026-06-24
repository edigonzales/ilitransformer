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

  assert.match(
    html,
    /Content-Security-Policy" content="default-src 'none'; style-src 'nonce-test-nonce'; script-src 'nonce-test-nonce';"/
  );
  assert.match(html, /<script nonce="test-nonce">/);
  assert.doesNotMatch(html, /onclick=/i);
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

test('renders coverage sections with navigation metadata', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.match(html, /Class Coverage/);
  assert.match(html, /Rule Coverage/);
  assert.match(html, /data-nav-line="10"/);
  assert.match(html, /data-nav-character="4"/);
  assert.match(html, /Missing mandatory/);
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
    diagnostics: [{ code: 'CODE', severity: 'warning', message: 'warn', line: 0, character: 0 }],
    coverageAvailable: true,
    coverageMessage: '',
    classCoverage: [
      {
        outputId: 'out',
        className: 'M.A',
        targeted: true,
        ruleIds: ['r1'],
        attributeCount: 2,
        assignedAttributeCount: 1,
        mandatoryMissingCount: 1,
        line: 10,
        character: 4
      }
    ],
    ruleCoverage: [
      {
        ruleId: 'r1',
        targetOutput: 'out',
        targetClass: 'M.A',
        attributes: [
          {
            name: 'Name',
            type: 'TEXT*60',
            cardinality: '1',
            mandatory: true,
            assigned: true,
            line: 12,
            character: 6
          },
          {
            name: 'Beschreibung',
            type: 'TEXT*200',
            cardinality: '1',
            mandatory: true,
            assigned: false,
            line: -1,
            character: -1
          }
        ],
        sources: [
          {
            alias: 's',
            inputIds: ['src'],
            sourceClass: 'M.A',
            usedAttributes: ['Name'],
            usedRoles: [],
            line: 11,
            character: 4
          }
        ],
        refs: [],
        directAssignmentCount: 1,
        bagAssignmentCount: 0,
        line: 10,
        character: 4
      }
    ]
  };
}
