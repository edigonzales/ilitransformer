const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const modPath = path.join(
  extensionRoot,
  'dist',
  'webview',
  'mappingOverviewHtml.js'
);
const { renderMappingOverviewHtml, escapeHtml, navLocation, isValidLocation } = require(modPath);

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

test('renders input IDs as navigable links when location present', () => {
  const html = renderMappingOverviewHtml(summaryWithLocations(), 'test-nonce');

  assert.match(html, /data-nav-line="2"/);
  assert.match(html, /data-nav-character="3"/);
});

test('renders output IDs as navigable links when location present', () => {
  const html = renderMappingOverviewHtml(summaryWithLocations(), 'test-nonce');

  assert.match(html, /data-nav-line="4"/);
  assert.match(html, /data-nav-character="5"/);
});

test('renders enum map IDs as navigable links when location present', () => {
  const html = renderMappingOverviewHtml(summaryWithLocations(), 'test-nonce');

  assert.match(html, /data-nav-line="6"/);
  assert.match(html, /data-nav-character="7"/);
});

test('renders rule IDs as navigable links when location present', () => {
  const html = renderMappingOverviewHtml(summaryWithLocations(), 'test-nonce');

  assert.match(html, /data-nav-line="8"/);
  assert.match(html, /data-nav-character="9"/);
});

test('renders range navigation attributes when end location set', () => {
  const summary = summaryWithRangeEnd();
  const html = renderMappingOverviewHtml(summary, 'test-nonce');

  assert.match(html, /data-nav-end-line="3"/);
  assert.match(html, /data-nav-end-character="15"/);
});

test('renders plain text when line is negative', () => {
  const summary = summaryWithNegativeLine();
  const html = renderMappingOverviewHtml(summary, 'test-nonce');

  assert.match(html, />negative_item</);
  assert.doesNotMatch(html, /data-nav-line="-1"/);
});

test('falls back to legacy line/character when location is absent', () => {
  const summary = summaryWithLineCharOnly();
  const html = renderMappingOverviewHtml(summary, 'test-nonce');

  assert.match(html, /data-nav-line="5"/);
  assert.match(html, /data-nav-character="0"/);
});

test('navLocation prefers location over line/character', () => {
  const item = {
    line: 1,
    character: 2,
    location: { line: 10, character: 20 }
  };
  const loc = navLocation(item);
  assert.equal(loc.line, 10);
  assert.equal(loc.character, 20);
});

test('navLocation falls back to line/character when no location', () => {
  const item = { line: 5, character: 3 };
  const loc = navLocation(item);
  assert.equal(loc.line, 5);
  assert.equal(loc.character, 3);
});

test('navLocation returns undefined for missing data', () => {
  assert.equal(navLocation({}), undefined);
  assert.equal(navLocation({ line: -1, character: 0 }), undefined);
});

test('isValidLocation returns true for valid location', () => {
  assert.equal(isValidLocation({ line: 0, character: 0 }), true);
  assert.equal(isValidLocation({ line: 5, character: 10 }), true);
});

test('isValidLocation returns false for invalid location', () => {
  assert.equal(isValidLocation(undefined), false);
  assert.equal(isValidLocation({ line: -1, character: 0 }), false);
  assert.equal(isValidLocation({ line: 0, character: -1 }), false);
});

test('escapeHtml escapes HTML special characters', () => {
  assert.equal(escapeHtml('<script>alert("xss")</script>'), '&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;');
});

test('renders class coverage with end-range when location has end positions', () => {
  const summary = summaryWithLocationEnds();
  const html = renderMappingOverviewHtml(summary, 'test-nonce');

  assert.match(html, /data-nav-end-line="12"/);
  assert.match(html, /data-nav-end-character="18"/);
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

function summaryWithLocations() {
  const s = summary();
  s.inputs[0].location = { line: 2, character: 3 };
  s.outputs[0].location = { line: 4, character: 5 };
  s.enumMaps[0].location = { line: 6, character: 7 };
  s.rules[0].location = { line: 8, character: 9 };
  return s;
}

function summaryWithRangeEnd() {
  const s = summary();
  s.inputs[0].location = { line: 2, character: 3, endLine: 3, endCharacter: 15 };
  return s;
}

function summaryWithNegativeLine() {
  const s = summary();
  s.inputs[0] = { id: 'negative_item', path: 'x', model: 'M', format: 'xtf', line: -1, character: -1 };
  s.outputs = [];
  s.enumMaps = [];
  s.rules = [];
  s.diagnostics = [];
  s.classCoverage = [];
  s.ruleCoverage = [];
  return s;
}

function summaryWithLineCharOnly() {
  const s = summary();
  s.inputs[0] = { id: 'legacy', path: 'x', model: 'M', format: 'xtf', line: 5, character: 0 };
  s.outputs = [];
  s.enumMaps = [];
  s.rules = [];
  s.diagnostics = [];
  s.classCoverage = [];
  s.ruleCoverage = [];
  return s;
}

function summaryWithLocationEnds() {
  const s = summary();
  s.classCoverage[0].location = { line: 10, character: 4, endLine: 12, endCharacter: 18 };
  return s;
}
