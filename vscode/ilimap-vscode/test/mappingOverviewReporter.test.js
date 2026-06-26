const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const modPath = path.join(extensionRoot, 'dist', 'overview', 'mappingOverviewReporter.js');
const { renderMappingReportMarkdown } = require(modPath);

test('renders report header with mapping name', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.match(md, /^# ilimap Mapping Report: TestProfile$/m);
});

test('renders summary section with metrics', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.match(md, /## Summary/);
  assert.match(md, /- Inputs: 2/);
  assert.match(md, /- Outputs: 1/);
  assert.match(md, /- Rules: 1/);
  assert.match(md, /- Enum Maps: 1/);
  assert.match(md, /- Bags: 0/);
  assert.match(md, /- Refs: 1/);
  assert.match(md, /- Diagnostics: 1/);
  assert.match(md, /- Errors: 0/);
  assert.match(md, /- Warnings: 1/);
});

test('renders inputs section as table', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.match(md, /## Inputs/);
  assert.match(md, /\| ID \| Path \| Model \| Format \|/);
  assert.match(md, /\| id_1 \| in1\.xtf \| ModelA \| xtf \|/);
  assert.match(md, /\| id_2 \| in2\.xtf \| ModelB \| xtf \|/);
});

test('renders outputs section as table', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.match(md, /## Outputs/);
  assert.match(md, /\| out_1 \| out\.xtf \| TargetModel \| xtf \|/);
});

test('renders rules section as table', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.match(md, /## Rules/);
  assert.match(md, /\| r1 \| out_1 \| TargetModel\.A \| ok \| 2 \| 3 \| 0 \| 1 \|/);
});

test('renders None for empty inputs', () => {
  const s = basicSummary();
  s.inputs = [];
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /## Inputs\n\nNone/);
});

test('renders None for empty outputs', () => {
  const s = basicSummary();
  s.outputs = [];
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /## Outputs\n\nNone/);
});

test('renders None for empty rules', () => {
  const s = basicSummary();
  s.rules = [];
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /## Rules\n\nNone/);
});

test('renders diagnostics section with table', () => {
  const md = renderMappingReportMarkdown(summaryWithDiagnostics());
  assert.match(md, /## Diagnostics/);
  assert.match(md, /\| Severity \| Code \| Message \| Rule \/ Input \/ Output \|/);
  assert.match(md, /\| error \| E001 \| missing attribute \| rule:r1 \|/);
  assert.match(md, /\| warning \| W001 \| unused input \| input:id_1 \|/);
});

test('renders no diagnostics when none', () => {
  const s = basicSummary();
  s.diagnostics = [];
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /## Diagnostics\n\nNo diagnostics\./);
});

test('renders diagnostics section hidden when includeDiagnostics is false', () => {
  const md = renderMappingReportMarkdown(summaryWithDiagnostics(), { includeDiagnostics: false });
  assert.doesNotMatch(md, /## Diagnostics/);
});

test('renders coverage section with rule coverage matrix', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.match(md, /## Coverage/);
  assert.match(md, /### Rule Coverage/);
  assert.match(md, /#### r1/);
  assert.match(md, /| Attribute | Status | Type | Mandatory | Source \/ Expression |/);
  assert.match(md, /| Name | mapped | TEXT\*60 | yes | — |/);
});

test('renders missing mandatory attribute in coverage', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.match(md, /| Beschreibung | missing | TEXT\*200 | yes |\s+\|/);
});

test('renders class coverage table', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.match(md, /### Class Coverage/);
  assert.match(md, /\| Class \| Output \| Targeted \| Attributes \| Assigned \| Missing Mandatory \|/);
  assert.match(md, /\| TargetModel\.A \| out_1 \| yes \| 2 \| 1 \| 1 \|/);
});

test('renders coverage unavailable message', () => {
  const s = basicSummary();
  s.coverageAvailable = false;
  s.coverageMessage = 'INTERLIS model not found.';
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /## Coverage/);
  assert.match(md, /INTERLIS model not found\./);
});

test('renders coverage section hidden when includeCoverage is false', () => {
  const md = renderMappingReportMarkdown(basicSummary(), { includeCoverage: false });
  assert.doesNotMatch(md, /## Coverage/);
});

test('renders source usage from grouped data', () => {
  const s = summaryWithSourceUsage();
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /## Source Usage/);
  assert.match(md, /### ModelA/);
  assert.match(md, /Aliases: a/);
  assert.match(md, /Inputs: id_1/);
  assert.match(md, /| Member | Kind | Status |/);
  assert.match(md, /\| Name \| attribute \| used \|/);
  assert.match(md, /\| Entstehung \| role \| unused \|/);
});

test('derives source usage from rule coverage when grouped data absent', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.match(md, /## Source Usage/);
  assert.match(md, /### ModelA/);
  assert.match(md, /Rule: r1/);
  assert.match(md, /Used attributes: Name/);
});

test('renders None when no source usage', () => {
  const s = basicSummary();
  s.ruleCoverage = [];
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /No source usage data is available\./);
});

test('source usage hidden when includeSourceUsage is false', () => {
  const md = renderMappingReportMarkdown(basicSummary(), { includeSourceUsage: false });
  assert.doesNotMatch(md, /## Source Usage/);
});

test('renders Mermaid flow chart when flow nodes present', () => {
  const s = summaryWithFlow();
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /## Flow/);
  assert.match(md, /```mermaid/);
  assert.match(md, /flowchart LR/);
  assert.match(md, /input_src\[\["src<br\/>in1\.xtf"\]/);
  assert.match(md, /rule_r1\[\["r1"\]/);
  assert.match(md, /output_out\[\["out"\]/);
  assert.match(md, /```$/m);
});

test('Mermaid flow escapes node IDs', () => {
  const s = summaryWithUnsafeFlow();
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /```mermaid/);
  assert.match(md, /rule_bad_script_\[\["badodescript<br\/>img"\]/);
  assert.doesNotMatch(md, /<script>/);
});

test('Mermaid flow escapes labels with quotes and backticks', () => {
  const s = summaryWithFlow();
  s.flowNodes[0].label = 'a"`<>label';
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /\[\["alabel<br\/>in1\.xtf"\]/);
  assert.doesNotMatch(md, /a"`<>/);
});

test('Mermaid flow hidden when includeMermaid is false', () => {
  const s = summaryWithFlow();
  const md = renderMappingReportMarkdown(s, { includeMermaid: false });
  assert.doesNotMatch(md, /## Flow/);
});

test('Mermaid section not rendered when no flow nodes', () => {
  const md = renderMappingReportMarkdown(basicSummary());
  assert.doesNotMatch(md, /## Flow/);
});

test('escapes pipe characters in table values', () => {
  const s = basicSummary();
  s.inputs[0].path = 'file|name.xtf';
  s.rules[0].id = 'r|1';
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /\| id_1 \| file\\\|name\.xtf \|/);
  assert.match(md, /\| r\\\|1 \|/);
});

test('escapes newlines in table values', () => {
  const s = basicSummary();
  s.inputs[0].model = 'Model\nA';
  const md = renderMappingReportMarkdown(s);
  assert.doesNotMatch(md, /Model\nA/);
  assert.match(md, /Model A/);
});

test('dangerous labels do not produce raw HTML in report', () => {
  const s = basicSummary();
  s.mappingName = '<img src=x onerror=alert(1)>';
  const md = renderMappingReportMarkdown(s);
  assert.doesNotMatch(md, /<img src=x onerror=alert\(1\)>/);
  assert.doesNotMatch(md, /<script>/i);
  assert.doesNotMatch(md, /<svg/i);
});

test('renders refs in coverage section when present', () => {
  const s = basicSummary();
  s.ruleCoverage[0].refs = ['ref1', 'ref2'];
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /Refs: ref1, ref2/);
});

test('empty coverage shows message', () => {
  const s = basicSummary();
  s.ruleCoverage = [];
  s.classCoverage = [];
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /No coverage data is available\./);
});

test('renders all sections by default', () => {
  const s = summaryWithFlow();
  const md = renderMappingReportMarkdown(s);
  assert.match(md, /## Summary/);
  assert.match(md, /## Inputs/);
  assert.match(md, /## Outputs/);
  assert.match(md, /## Rules/);
  assert.match(md, /## Flow/);
  assert.match(md, /## Coverage/);
  assert.match(md, /## Source Usage/);
  assert.match(md, /## Diagnostics/);
});

function basicSummary() {
  return {
    available: true,
    message: '',
    mappingName: 'TestProfile',
    inputCount: 2,
    outputCount: 1,
    ruleCount: 1,
    enumMapCount: 1,
    bagCount: 0,
    refCount: 1,
    errorCount: 0,
    warningCount: 1,
    informationCount: 0,
    hintCount: 0,
    inputs: [
      { id: 'id_1', path: 'in1.xtf', model: 'ModelA', format: 'xtf' },
      { id: 'id_2', path: 'in2.xtf', model: 'ModelB', format: 'xtf' }
    ],
    outputs: [
      { id: 'out_1', path: 'out.xtf', model: 'TargetModel', format: 'xtf' }
    ],
    enumMaps: [{ id: 'QualityMap', entryCount: 3 }],
    rules: [
      {
        id: 'r1',
        targetOutput: 'out_1',
        targetClass: 'TargetModel.A',
        sourceCount: 2,
        assignmentCount: 3,
        bagCount: 0,
        refCount: 1,
        status: 'ok'
      }
    ],
    diagnostics: [{ code: 'W1', severity: 'warning', message: 'test warning', line: 0, character: 0 }],
    coverageAvailable: true,
    coverageMessage: '',
    classCoverage: [
      {
        outputId: 'out_1',
        className: 'TargetModel.A',
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
        targetOutput: 'out_1',
        targetClass: 'TargetModel.A',
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
            alias: 'a',
            inputIds: ['id_1'],
            sourceClass: 'ModelA',
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

function summaryWithDiagnostics() {
  const s = basicSummary();
  s.errorCount = 1;
  s.warningCount = 2;
  s.diagnostics = [
    {
      code: 'E001',
      severity: 'error',
      message: 'missing attribute',
      line: 5,
      character: 0,
      ruleId: 'r1'
    },
    {
      code: 'W001',
      severity: 'warning',
      message: 'unused input',
      line: 2,
      character: 0,
      inputId: 'id_1'
    },
    {
      code: 'W002',
      severity: 'warning',
      message: 'orphan output',
      line: 8,
      character: 0,
      outputId: 'out_1'
    }
  ];
  return s;
}

function summaryWithFlow() {
  const s = basicSummary();
  s.flowNodes = [
    { nodeId: 'input:src', kind: 'input', label: 'src', detail: 'in1.xtf', status: 'ok' },
    { nodeId: 'rule:r1', kind: 'rule', label: 'r1', status: 'ok' },
    { nodeId: 'output:out', kind: 'output', label: 'out', status: 'ok' }
  ];
  s.flowEdges = [
    { id: 'e1', fromNodeId: 'input:src', toNodeId: 'rule:r1', kind: 'sourceToRule', status: 'ok' },
    { id: 'e2', fromNodeId: 'rule:r1', toNodeId: 'output:out', kind: 'ruleToTarget', status: 'ok' }
  ];
  return s;
}

function summaryWithUnsafeFlow() {
  const s = basicSummary();
  s.flowNodes = [
    { nodeId: 'rule:bad<script>', kind: 'rule', label: 'bad\node<script>', detail: '<img>', status: 'ok' }
  ];
  s.flowEdges = [];
  return s;
}

function summaryWithSourceUsage() {
  const s = basicSummary();
  s.sourceUsage = [
    {
      inputIds: ['id_1'],
      sourceClass: 'ModelA',
      aliases: ['a'],
      attributes: [
        { name: 'Name', kind: 'attribute', status: 'used', usedBy: [] }
      ],
      roles: [
        { name: 'Entstehung', kind: 'role', status: 'unused', usedBy: [] }
      ]
    }
  ];
  return s;
}
