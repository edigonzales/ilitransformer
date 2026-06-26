const assert = require('node:assert/strict');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const distProviderPath = path.join(extensionRoot, 'dist', 'overview', 'mappingExplorerProvider.js');

function makeVscodeMock() {
  const emitted = [];

  return {
    TreeItem: class TreeItem {
      constructor(label, collapsibleState) {
        this.label = label;
        this.collapsibleState = collapsibleState;
      }
    },
    TreeItemCollapsibleState: {
      None: 0,
      Collapsed: 1,
      Expanded: 2
    },
    ThemeIcon: class ThemeIcon {
      constructor(id) {
        this.id = id;
      }
    },
    EventEmitter: class EventEmitter {
      constructor() {
        this.event = (listener) => {
          this.listener = listener;
          return { dispose() {} };
        };
      }
      fire(value) {
        emitted.push(value);
      }
    }
  };
}

function emptySummary() {
  return {
    available: true,
    message: '',
    mappingName: '',
    inputCount: 0,
    outputCount: 0,
    ruleCount: 0,
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
    rules: [],
    diagnostics: []
  };
}

function populatedSummary() {
  return {
    available: true,
    message: '',
    mappingName: 'Profile',
    inputCount: 2,
    outputCount: 1,
    ruleCount: 3,
    enumMapCount: 0,
    bagCount: 0,
    refCount: 0,
    errorCount: 1,
    warningCount: 2,
    informationCount: 0,
    hintCount: 0,
    inputs: [
      {
        id: 'dm01',
        path: 'dm01.itf',
        model: 'DM01',
        format: 'itf',
        nodeId: 'input:dm01',
        location: { line: 5, character: 4 }
      },
      {
        id: 'extra',
        path: 'extra.csv',
        model: 'Extra',
        format: 'csv',
        nodeId: 'input:extra',
        line: 12,
        character: 2
      }
    ],
    outputs: [
      {
        id: 'dmav',
        path: 'dmav.xtf',
        model: 'DMAV',
        format: 'xtf',
        nodeId: 'output:dmav',
        location: { line: 20, character: 5 }
      }
    ],
    enumMaps: [],
    rules: [
      {
        id: 'lfp3',
        targetOutput: 'dmav',
        targetClass: 'DMAV.LFP3',
        sourceCount: 1,
        assignmentCount: 14,
        bagCount: 0,
        refCount: 0,
        status: 'warning',
        nodeId: 'rule:lfp3',
        location: { line: 30, character: 2 }
      },
      {
        id: 'r2',
        targetOutput: 'dmav',
        targetClass: 'DMAV.Other',
        sourceCount: 0,
        assignmentCount: 0,
        bagCount: 0,
        refCount: 0,
        status: 'error',
        nodeId: 'rule:r2',
        location: { line: 50, character: 2 }
      },
      {
        id: 'r3',
        targetOutput: 'dmav',
        targetClass: 'DMAV.Third',
        sourceCount: 1,
        assignmentCount: 3,
        bagCount: 0,
        refCount: 0,
        status: 'ok',
        nodeId: 'rule:r3'
      }
    ],
    diagnostics: [
      {
        code: 'W001',
        severity: 'warning',
        message: 'Missing attribute mapping',
        line: 32,
        character: 5,
        nodeId: 'diag1'
      },
      {
        code: 'E001',
        severity: 'error',
        message: 'Invalid expression',
        line: 55,
        character: 1,
        nodeId: 'diag2',
        location: { line: 55, character: 1, endLine: 55, endCharacter: 10 }
      }
    ]
  };
}

function populatedSummaryWithCoverage() {
  const s = populatedSummary();
  s.coverageAvailable = true;
  s.coverageMessage = '';
  s.classCoverage = [
    {
      outputId: 'dmav',
      className: 'DMAV.LFP3',
      targeted: true,
      ruleIds: ['lfp3'],
      attributeCount: 14,
      assignedAttributeCount: 12,
      mandatoryMissingCount: 2,
      line: 30,
      character: 2,
      nodeId: 'target:dmav:DMAV.LFP3',
      location: { line: 30, character: 2 }
    }
  ];
  return s;
}

test('provider getChildren with no summary returns placeholder', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  const children = await provider.getChildren();

  assert.equal(children.length, 1);
  assert.equal(children[0].kind, 'root');
  assert.equal(children[0].label, 'No mapping overview loaded');

  Module._load = originalLoad;
});

test('provider getChildren returns root groups from populated summary', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  const summary = populatedSummary();
  provider.refresh(summary, 'file:///tmp/profile.ilimap');

  const children = await provider.getChildren();

  const kinds = children.map(c => c.kind);
  assert.ok(kinds.includes('inputs'));
  assert.ok(kinds.includes('outputs'));
  assert.ok(kinds.includes('rules'));

  const inputsItem = children.find(c => c.kind === 'inputs');
  assert.equal(inputsItem.label, 'Inputs (2)');
  assert.equal(inputsItem.collapsibleState, vscodeMock.TreeItemCollapsibleState.Collapsed);

  const outputsItem = children.find(c => c.kind === 'outputs');
  assert.equal(outputsItem.label, 'Outputs (1)');

  const rulesItem = children.find(c => c.kind === 'rules');
  assert.equal(rulesItem.label, 'Rules (3)');

  const problemsItem = children.find(c => c.kind === 'problems');
  assert.ok(problemsItem);
  assert.equal(problemsItem.label, 'Problems (3)');

  Module._load = originalLoad;
});

test('provider getChildren returns input items with location commands', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  const summary = populatedSummary();
  provider.refresh(summary, 'file:///tmp/profile.ilimap');

  const rootChildren = await provider.getChildren();
  const inputsItem = rootChildren.find(c => c.kind === 'inputs');
  const inputItems = await provider.getChildren(inputsItem);

  assert.equal(inputItems.length, 2);

  const dm01Item = inputItems.find(i => i.nodeId === 'input:dm01');
  assert.ok(dm01Item);
  assert.equal(dm01Item.kind, 'input');
  assert.equal(dm01Item.label, 'dm01 · itf');
  assert.equal(dm01Item.description, 'dm01.itf');
  assert.ok(dm01Item.command);
  assert.equal(dm01Item.command.command, 'ilimap.mappingExplorer.revealInEditor');
  assert.equal(dm01Item.command.title, 'Reveal in Editor');
  assert.deepEqual(dm01Item.command.arguments[0], 'file:///tmp/profile.ilimap');
  assert.deepEqual(dm01Item.command.arguments[1], { line: 5, character: 4 });

  const extraItem = inputItems.find(i => i.nodeId === 'input:extra');
  assert.ok(extraItem);
  assert.equal(extraItem.label, 'extra · csv');
  assert.ok(extraItem.command);
  assert.deepEqual(extraItem.command.arguments[1], { line: 12, character: 2 });

  Module._load = originalLoad;
});

test('provider getChildren returns output items with location commands', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  const summary = populatedSummary();
  provider.refresh(summary, 'file:///tmp/profile.ilimap');

  const rootChildren = await provider.getChildren();
  const outputsItem = rootChildren.find(c => c.kind === 'outputs');
  const outputItems = await provider.getChildren(outputsItem);

  assert.equal(outputItems.length, 1);
  const dmavItem = outputItems[0];
  assert.equal(dmavItem.kind, 'output');
  assert.equal(dmavItem.label, 'dmav · xtf');
  assert.equal(dmavItem.description, 'dmav.xtf');
  assert.ok(dmavItem.command);
  assert.equal(dmavItem.command.command, 'ilimap.mappingExplorer.revealInEditor');

  Module._load = originalLoad;
});

test('provider getChildren returns rule items with status and icons', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  const summary = populatedSummary();
  provider.refresh(summary, 'file:///tmp/profile.ilimap');

  const rootChildren = await provider.getChildren();
  const rulesItem = rootChildren.find(c => c.kind === 'rules');
  const ruleItems = await provider.getChildren(rulesItem);

  assert.equal(ruleItems.length, 3);

  const lfp3 = ruleItems.find(r => r.nodeId === 'rule:lfp3');
  assert.ok(lfp3);
  assert.equal(lfp3.label, 'lfp3 · DMAV.LFP3');
  assert.equal(lfp3.description, 'warning');
  assert.ok(lfp3.iconPath);
  assert.equal(lfp3.iconPath.id, 'warning');
  assert.ok(lfp3.command);

  const r2 = ruleItems.find(r => r.nodeId === 'rule:r2');
  assert.ok(r2);
  assert.equal(r2.description, 'error');
  assert.ok(r2.iconPath);
  assert.equal(r2.iconPath.id, 'error');

  const r3 = ruleItems.find(r => r.nodeId === 'rule:r3');
  assert.ok(r3);
  assert.equal(r3.description, 'ok');
  assert.equal(r3.command, undefined);

  Module._load = originalLoad;
});

test('provider getChildren returns coverage items when coverageAvailable', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  const summary = populatedSummaryWithCoverage();
  provider.refresh(summary, 'file:///tmp/profile.ilimap');

  const rootChildren = await provider.getChildren();
  const coverageItem = rootChildren.find(c => c.kind === 'coverage');
  assert.ok(coverageItem);
  assert.equal(coverageItem.label, 'Coverage (1)');

  const coverageItems = await provider.getChildren(coverageItem);
  assert.equal(coverageItems.length, 1);
  const classItem = coverageItems[0];
  assert.equal(classItem.kind, 'coverageClass');
  assert.match(classItem.label, /DMAV.LFP3/);
  assert.match(classItem.label, /12\/14/);
  assert.match(classItem.label, /2 missing/);
  assert.ok(classItem.iconPath);
  assert.equal(classItem.iconPath.id, 'warning');
  assert.ok(classItem.command);

  Module._load = originalLoad;
});

test('coverage section not shown when coverageAvailable is falsy', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  provider.refresh(populatedSummary(), 'file:///x');

  const rootChildren = await provider.getChildren();
  const coverageItem = rootChildren.find(c => c.kind === 'coverage');
  assert.equal(coverageItem, undefined);

  Module._load = originalLoad;
});

test('provider getChildren returns problem items with diagnostics', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  const summary = populatedSummary();
  provider.refresh(summary, 'file:///tmp/profile.ilimap');

  const rootChildren = await provider.getChildren();
  const problemsItem = rootChildren.find(c => c.kind === 'problems');
  const problemItems = await provider.getChildren(problemsItem);

  assert.equal(problemItems.length, 2);

  const warningItem = problemItems[0];
  assert.equal(warningItem.kind, 'diagnostic');
  assert.match(warningItem.label, /W001/);
  assert.match(warningItem.label, /Missing attribute mapping/);
  assert.equal(warningItem.description, 'warning L32');
  assert.ok(warningItem.iconPath);
  assert.equal(warningItem.iconPath.id, 'warning');
  assert.ok(warningItem.command);
  assert.deepEqual(warningItem.command.arguments[1], { line: 32, character: 5 });

  const errorItem = problemItems[1];
  assert.equal(errorItem.kind, 'diagnostic');
  assert.match(errorItem.label, /E001/);
  assert.ok(errorItem.iconPath);
  assert.equal(errorItem.iconPath.id, 'error');
  assert.deepEqual(errorItem.command.arguments[1], { line: 55, character: 1, endLine: 55, endCharacter: 10 });

  Module._load = originalLoad;
});

test('problems section not shown when no errors or warnings', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  const summary = emptySummary();
  provider.refresh(summary);

  const rootChildren = await provider.getChildren();
  const problemsItem = rootChildren.find(c => c.kind === 'problems');
  assert.equal(problemsItem, undefined);

  Module._load = originalLoad;
});

test('refresh emits change event and updates summary', async () => {
  const vscodeMock = makeVscodeMock();
  const emitted = [];
  vscodeMock.EventEmitter = class EventEmitter {
    constructor() {
      this.event = () => ({ dispose() {} });
    }
    fire(value) {
      emitted.push(value);
    }
  };

  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  const children1 = await provider.getChildren();
  assert.equal(children1[0].label, 'No mapping overview loaded');
  assert.equal(emitted.length, 0);

  const summary = populatedSummary();
  provider.refresh(summary, 'file:///test.ilimap');
  assert.equal(emitted.length, 1);
  assert.equal(emitted[0], undefined);

  const children2 = await provider.getChildren();
  assert.ok(children2.length >= 3);

  Module._load = originalLoad;
});

test('getCurrentUri returns the URI set via refresh', () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  assert.equal(provider.getCurrentUri(), undefined);

  provider.refresh(populatedSummary(), 'file:///tmp/test.ilimap');
  assert.equal(provider.getCurrentUri(), 'file:///tmp/test.ilimap');

  Module._load = originalLoad;
});

test('items without URI do not get location commands', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  provider.refresh(populatedSummary());

  const rootChildren = await provider.getChildren();
  const inputsItem = rootChildren.find(c => c.kind === 'inputs');
  const inputItems = await provider.getChildren(inputsItem);

  const dm01Item = inputItems.find(i => i.nodeId === 'input:dm01');
  assert.equal(dm01Item.command, undefined);

  Module._load = originalLoad;
});

test('empty summary shows correct root groups without coverage or problems', async () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider } = require(distProviderPath);

  const provider = new MappingExplorerProvider();
  provider.refresh(emptySummary(), 'file:///x');

  const rootChildren = await provider.getChildren();
  const kinds = rootChildren.map(c => c.kind);
  assert.ok(kinds.includes('inputs'));
  assert.ok(kinds.includes('outputs'));
  assert.ok(kinds.includes('rules'));

  const inputsItem = rootChildren.find(c => c.kind === 'inputs');
  assert.equal(inputsItem.label, 'Inputs (0)');

  const outputsItem = rootChildren.find(c => c.kind === 'outputs');
  assert.equal(outputsItem.label, 'Outputs (0)');

  const rulesItem = rootChildren.find(c => c.kind === 'rules');
  assert.equal(rulesItem.label, 'Rules (0)');

  const emptyInputItems = await provider.getChildren(inputsItem);
  assert.equal(emptyInputItems.length, 0);

  Module._load = originalLoad;
});

test('setMappingExplorerInstance and getMappingExplorerInstance work', () => {
  const vscodeMock = makeVscodeMock();
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distProviderPath];
  const { MappingExplorerProvider, setMappingExplorerInstance, getMappingExplorerInstance } = require(distProviderPath);

  assert.equal(getMappingExplorerInstance(), undefined);

  const provider = new MappingExplorerProvider();
  setMappingExplorerInstance(provider);

  assert.equal(getMappingExplorerInstance(), provider);

  Module._load = originalLoad;
});
