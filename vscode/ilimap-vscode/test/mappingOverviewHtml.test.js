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
  assert.match(html, /class="coverage-matrix"/);
});

test('renders the target coverage matrix with header columns and status tags', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.match(html, /<table class="coverage-matrix">/);
  assert.match(html, /<th>Attribute<\/th>/);
  assert.match(html, /<th>Status<\/th>/);
  assert.match(html, /<th>Source \/ Expression<\/th>/);
  assert.match(html, /class="tag tag-mapped">mapped</);
  assert.match(html, />missing</);
});

test('renders derived status from assigned/mandatory when server status is absent', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.match(html, /tag-mapped">mapped</);
  assert.match(html, /tag-missing warning">missing</);
});

test('uses server-provided status when present', () => {
  const s = summary();
  s.ruleCoverage[0].attributes[0].status = 'enumMap';
  const html = renderMappingOverviewHtml(s, 'test-nonce');

  assert.match(html, /tag-enumMap">enumMap</);
});

test('marks missing mandatory attributes with a warning row class and data attributes', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.match(html, /<tr class="coverage-row-missing" data-missing="true" data-mandatory="true">/);
  assert.match(html, /class="req-marker"/);
});

test('escapes coverage expressions and source summaries', () => {
  const html = renderMappingOverviewHtml(summaryWithUnsafeExpression(), 'test-nonce');

  assert.match(html, /<code>&lt;b&gt;evil&lt;\/b&gt;<\/code>/);
  assert.doesNotMatch(html, /<code><b>evil<\/b><\/code>/);
});

test('renders a coverage filter bar without inline handlers', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.match(html, /data-filter-target="rule-coverage"/);
  assert.match(html, /data-filter-value="missing"/);
  assert.match(html, /data-filter-value="mandatory"/);
  assert.doesNotMatch(html, /onclick=/i);
});

test('renders Source Usage section from grouped server source usage', () => {
  const html = renderMappingOverviewHtml(summaryWithSourceUsage(), 'test-nonce');

  assert.match(html, /<section class="source-usage"[^>]*>/);
  assert.match(html, /Source Usage/);
  assert.match(html, /Attributes:/);
  assert.match(html, /Roles:/);
  assert.match(html, /data-usage-status="used"/);
  assert.match(html, /data-usage-status="unused"/);
  assert.match(html, />Entstehung</);
  assert.match(html, /data-filter-value="unused"/);
});

test('derives Source Usage from rule coverage sources when grouped usage is absent', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.match(html, /Source Usage/);
  assert.match(html, /Attributes:/);
  assert.match(html, /data-usage-status="used"/);
});

test('escapes source usage member names', () => {
  const s = summaryWithSourceUsage();
  s.sourceUsage[0].attributes.push({
    name: '<img src=x onerror=alert(3)>',
    kind: 'attribute',
    status: 'used',
    usedBy: []
  });
  const html = renderMappingOverviewHtml(s, 'test-nonce');

  assert.match(html, /&lt;img src=x onerror=alert\(3\)&gt;/);
  assert.doesNotMatch(html, /<img src=x onerror=alert\(3\)>/);
});

test('renders None when there is no source usage', () => {
  const html = renderMappingOverviewHtml(summaryWithoutSourceUsage(), 'test-nonce');

  assert.match(html, /<section class="source-usage"[^>]*>\s*<h2>Source Usage<\/h2>\s*<p class="empty">None<\/p>/);
});

test('keeps strict CSP and no editable controls with coverage matrix and source usage', () => {
  const html = renderMappingOverviewHtml(summaryWithSourceUsage(), 'test-nonce');

  assert.match(
    html,
    /Content-Security-Policy" content="default-src 'none'; style-src 'nonce-test-nonce'; script-src 'nonce-test-nonce';"/
  );
  assert.doesNotMatch(html, /<button\b/i);
  assert.doesNotMatch(html, /<input\b/i);
  assert.doesNotMatch(html, /<form\b/i);
  assert.doesNotMatch(html, /contenteditable/i);
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

test('renders a refresh action link without inline handlers', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.match(html, /data-action="refresh"/);
  assert.match(html, />Refresh</);
  assert.doesNotMatch(html, /onclick=/i);
  assert.doesNotMatch(html, /<button\b/i);
  assert.doesNotMatch(html, /<input\b/i);
  assert.doesNotMatch(html, /<form\b/i);
});

test('renders loading banner from render state', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', { refreshState: 'loading' });

  assert.match(html, /Loading mapping overview/);
});

test('renders stale banner from render state', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', { refreshState: 'stale' });

  assert.match(html, /Stale: document changed/);
});

test('renders last-updated banner from render state', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', {
    refreshState: 'idle',
    lastUpdated: '10:42:31'
  });

  assert.match(html, /Last updated: 10:42:31/);
});

test('renders error banner with escaped message', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', {
    refreshState: 'error',
    errorMessage: '<script>x</script>'
  });

  assert.match(html, /Failed to refresh: &lt;script&gt;x&lt;\/script&gt;/);
  assert.doesNotMatch(html, /<script>x<\/script>/);
});

test('keeps strict CSP and no editable controls with render state', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', {
    refreshState: 'error',
    errorMessage: 'boom'
  });

  assert.match(
    html,
    /Content-Security-Policy" content="default-src 'none'; style-src 'nonce-test-nonce'; script-src 'nonce-test-nonce';"/
  );
  assert.doesNotMatch(html, /<button\b/i);
  assert.doesNotMatch(html, /<input\b/i);
  assert.doesNotMatch(html, /<form\b/i);
  assert.doesNotMatch(html, /contenteditable/i);
});

test('renders Inspect link on each rule', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.match(html, /data-action="inspect-rule"/);
  assert.match(html, /data-rule-id="r1"/);
  assert.match(html, />Inspect</);
});

test('inspect links do not use inline event handlers', () => {
  const html = renderMappingOverviewHtml(summaryWithLocations(), 'test-nonce');

  assert.match(html, /data-action="inspect-rule"/);
  assert.doesNotMatch(html, /onclick=/i);
});

test('rule inspector renders when detail is available', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, baseDetail());

  assert.match(html, /Rule r1/);
  assert.match(html, /rule-inspector/);
});

test('rule inspector renders target section', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, baseDetail());

  assert.match(html, /Target/);
  assert.match(html, /M\.A/);
  assert.match(html, /out/);
});

test('rule inspector renders source section', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, baseDetail());

  assert.match(html, /Sources/);
  assert.match(html, /s/);
  assert.match(html, /src/);
});

test('rule inspector renders assignments with kind tags', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, baseDetail());

  assert.match(html, /Assignments/);
  assert.match(html, /Name/);
  assert.match(html, /tag-copy/);
  assert.match(html, />copy</);
});

test('rule inspector renders expressions in code blocks', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, baseDetail());

  assert.match(html, /<code>s\.Name<\/code>/);
});

test('rule inspector renders empty sections as None', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, baseDetail());

  assert.match(html, /Defaults: None/);
  assert.doesNotMatch(html, /Joins<\/h3>/);
  assert.doesNotMatch(html, /Identity<\/h3>/);
  assert.doesNotMatch(html, /Refs<\/h3>/);
  assert.doesNotMatch(html, /Loss<\/h3>/);
});

test('rule inspector renders joins', () => {
  const detail = fullDetail();
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, detail);

  assert.match(html, /Joins/);
  assert.match(html, />left</);
  assert.match(html, /a.*b/);
  assert.match(html, /<code>eq\(a\.Key, b\.Key\)<\/code>/);
});

test('rule inspector renders identity', () => {
  const detail = fullDetail();
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, detail);

  assert.match(html, /Identity/);
  assert.match(html, /<code>a\.Id<\/code>/);
});

test('rule inspector renders refs', () => {
  const detail = fullDetail();
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, detail);

  assert.match(html, /Refs/);
  assert.match(html, /Parent/);
  assert.match(html, /required/);
  assert.match(html, />r1</);
});

test('rule inspector renders bags', () => {
  const detail = fullDetail();
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, detail);

  assert.match(html, /Bags/);
  assert.match(html, /Outer/);
  assert.match(html, />O</);
  assert.match(html, /Inner/);
});

test('rule inspector renders losses', () => {
  const detail = fullDetail();
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, detail);

  assert.match(html, /Loss/);
  assert.match(html, /NOT_MAPPED/);
});

test('rule inspector renders metadata', () => {
  const detail = fullDetail();
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, detail);

  assert.match(html, /Metadata/);
  assert.match(html, /forward/);
});

test('rule inspector renders diagnostics', () => {
  const detail = fullDetail();
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, detail);

  assert.match(html, /Diagnostics/);
  assert.match(html, /TEST_WARNING/);
});

test('rule inspector escapes all server values', () => {
  const detail = {
    ...fullDetail(),
    sources: [{
      alias: '<img src=x>',
      inputIds: ['<script>'],
      className: '<svg onload=alert(1)>'
    }]
  };
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, detail);

  assert.match(html, /&lt;img src=x&gt;/);
  assert.match(html, /&lt;script&gt;/);
  assert.match(html, /&lt;svg onload=alert\(1\)&gt;/);
  assert.doesNotMatch(html, /<img src=x>/);
  assert.doesNotMatch(html, /<script>/);
  assert.doesNotMatch(html, /<svg onload/);
});

test('CSP and no editable controls preserved with rule inspector', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, fullDetail());

  assert.match(
    html,
    /Content-Security-Policy" content="default-src 'none'; style-src 'nonce-test-nonce'; script-src 'nonce-test-nonce';"/
  );
  assert.doesNotMatch(html, /<button\b/i);
  assert.doesNotMatch(html, /<input\b/i);
  assert.doesNotMatch(html, /<form\b/i);
  assert.doesNotMatch(html, /contenteditable/i);
});

test('rule inspector shows unavailable message when detail not available', () => {
  const detail = unavailableDetail();
  const html = renderMappingOverviewHtml(summary(), 'test-nonce', undefined, detail);

  assert.match(html, /Rule detail unavailable/);
});

test('no inspector section when detail is undefined', () => {
  const html = renderMappingOverviewHtml(summary(), 'test-nonce');

  assert.doesNotMatch(html, /<section class="rule-inspector"/);
  assert.doesNotMatch(html, /Rule &lt;img/);
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

function summaryWithUnsafeExpression() {
  const s = summary();
  s.ruleCoverage[0].attributes[0].expression = '<b>evil</b>';
  s.ruleCoverage[0].attributes[0].sourceSummary = undefined;
  return s;
}

function summaryWithSourceUsage() {
  const s = summary();
  s.sourceUsage = [
    {
      inputIds: ['src'],
      sourceClass: 'M.A',
      aliases: ['s'],
      attributes: [
        { name: 'Name', kind: 'attribute', status: 'used', usedBy: [] },
        { name: 'Beschreibung', kind: 'attribute', status: 'unused', usedBy: [] }
      ],
      roles: [{ name: 'Entstehung', kind: 'role', status: 'used', usedBy: [] }]
    }
  ];
  return s;
}

function summaryWithoutSourceUsage() {
  const s = summary();
  s.sourceUsage = [];
  s.ruleCoverage = [];
  return s;
}

function baseDetail() {
  return {
    available: true,
    message: '',
    ruleId: 'r1',
    nodeId: 'rule:r1',
    location: { line: 8, character: 9 },
    target: {
      outputId: 'out',
      className: 'M.A',
      location: { line: 8, character: 11 }
    },
    sources: [
      {
        alias: 's',
        inputIds: ['src'],
        className: 'M.A',
        where: undefined,
        location: { line: 9, character: 7 }
      }
    ],
    joins: [],
    identity: [],
    assignments: [
      {
        targetAttribute: 'Name',
        expression: 's.Name',
        kind: 'copy',
        dependencies: [{ kind: 'sourceAttribute', alias: 's', member: 'Name' }],
        location: { line: 11, character: 9 }
      }
    ],
    defaults: [],
    bags: [],
    refs: [],
    losses: [],
    metadata: undefined,
    diagnostics: []
  };
}

function fullDetail() {
  return {
    available: true,
    message: '',
    ruleId: 'r1',
    nodeId: 'rule:r1',
    location: { line: 8, character: 9 },
    target: {
      outputId: 'out',
      className: 'M.A',
      location: { line: 8, character: 11 }
    },
    sources: [
      {
        alias: 's',
        inputIds: ['src'],
        className: 'M.A',
        location: { line: 9, character: 7 }
      }
    ],
    joins: [
      {
        type: 'left',
        leftAlias: 'a',
        rightAlias: 'b',
        condition: 'eq(a.Key, b.Key)',
        location: { line: 10, character: 7 }
      }
    ],
    identity: [
      { expression: 'a.Id', location: { line: 10, character: 14 } }
    ],
    assignments: [
      {
        targetAttribute: 'Name',
        expression: 's.Name',
        kind: 'copy',
        dependencies: [{ kind: 'sourceAttribute', alias: 's', member: 'Name' }],
        location: { line: 12, character: 9 }
      }
    ],
    defaults: [
      {
        targetAttribute: 'Status',
        expression: '"active"',
        kind: 'default',
        dependencies: [],
        location: { line: 14, character: 9 }
      }
    ],
    bags: [
      {
        name: 'Outer',
        targetAttribute: 'OuterAttr',
        structureClass: 'M.Outer',
        source: {
          alias: 'o',
          inputIds: ['src'],
          className: 'M.Outer'
        },
        assignments: [
          {
            targetAttribute: 'O',
            expression: 'o.O',
            kind: 'copy',
            dependencies: [{ kind: 'sourceAttribute', alias: 'o', member: 'O' }],
            location: { line: 100, character: 11 }
          }
        ],
        nestedBags: [
          {
            name: 'Inner',
            assignments: [
              {
                targetAttribute: 'I',
                expression: 'i.I',
                kind: 'copy',
                dependencies: [{ kind: 'sourceAttribute', alias: 'i', member: 'I' }],
                location: { line: 200, character: 13 }
              }
            ],
            nestedBags: [],
            location: { line: 200, character: 9 }
          }
        ],
        location: { line: 100, character: 7 }
      }
    ],
    refs: [
      {
        name: 'Parent',
        association: 'M.ParentAssoc',
        role: 'ParentRole',
        required: true,
        targetRuleId: 'r1',
        sourceRef: 's.ParentId',
        location: { line: 300, character: 7 }
      }
    ],
    losses: [
      {
        sourcePath: 's.Obsolete',
        reasonCode: 'NOT_MAPPED',
        description: 'Field not in target',
        when: 'defined(s.Obsolete)',
        location: { line: 400, character: 7 }
      }
    ],
    metadata: {
      direction: 'forward',
      roundtrip: 'notGuaranteed',
      lossiness: 'minor',
      location: { line: 500, character: 7 }
    },
    diagnostics: [
      {
        code: 'TEST_WARNING',
        severity: 'warning',
        message: 'Test warning message',
        line: 0,
        character: 0,
        nodeId: 'diagnostic:TEST_WARNING:0:0',
        location: { line: 0, character: 0 }
      }
    ]
  };
}

function unavailableDetail() {
  return {
    available: false,
    message: 'Rule detail unavailable.',
    ruleId: 'bad',
    sources: [],
    joins: [],
    identity: [],
    assignments: [],
    defaults: [],
    bags: [],
    refs: [],
    losses: [],
    diagnostics: []
  };
}
