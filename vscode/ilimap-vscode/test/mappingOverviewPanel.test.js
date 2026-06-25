const assert = require('node:assert/strict');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const distPanelPath = path.join(extensionRoot, 'dist', 'webview', 'mappingOverviewPanel.js');

test('openMappingOverview requests active document summary and renders webview', async (t) => {
  const requested = [];
  const panels = [];
  const messages = [];
  const outputLines = [];
  const progressTitles = [];
  const openedDocuments = [];
  const selections = [];
  const revealedRanges = [];

  const vscodeMock = {
    ViewColumn: {
      One: 1,
      Beside: 2
    },
    ProgressLocation: {
      Notification: 15
    },
    TextEditorRevealType: {
      InCenterIfOutsideViewport: 2
    },
    Position: class Position {
      constructor(line, character) {
        this.line = line;
        this.character = character;
      }
    },
    Range: class Range {
      constructor(start, end) {
        this.start = start;
        this.end = end;
      }
    },
    Selection: class Selection {
      constructor(anchor, active) {
        this.anchor = anchor;
        this.active = active;
      }
    },
    Uri: {
      parse(value) {
        return { value };
      }
    },
    workspace: {
      async openTextDocument(uri) {
        openedDocuments.push(uri.value);
        return { uri };
      }
    },
    window: {
      activeTextEditor: {
        document: {
          languageId: 'ilimap',
          uri: {
            fsPath: '/tmp/profile.ilimap',
            toString() {
              return 'file:///tmp/profile.ilimap';
            }
          }
        }
      },
      createWebviewPanel(viewType, title, column, options) {
        const panel = {
          viewType,
          title,
          column,
          options,
          webview: {
            html: '',
            onDidReceiveMessage(callback) {
              panel.messageCallback = callback;
            }
          },
          reveal(nextColumn) {
            panel.revealed = nextColumn;
          },
          onDidDispose(callback) {
            panel.disposeCallback = callback;
          }
        };
        panels.push(panel);
        return panel;
      },
      showInformationMessage(message) {
        messages.push({ kind: 'info', message });
      },
      showErrorMessage(message) {
        messages.push({ kind: 'error', message });
      },
      withProgress(options, task) {
        progressTitles.push(options.title);
        return task();
      },
      async showTextDocument(document, column, preserveFocus) {
        return {
          document,
          column,
          preserveFocus,
          set selection(value) {
            selections.push(value);
          },
          revealRange(range, revealType) {
            revealedRanges.push({ range, revealType });
          }
        };
      }
    }
  };

  const clientMock = {
    async sendRequest(method, params) {
      requested.push({ method, params });
      return {
        available: true,
        message: '',
        mappingName: 'Profile',
        inputCount: 1,
        outputCount: 1,
        ruleCount: 1,
        enumMapCount: 0,
        bagCount: 0,
        refCount: 0,
        errorCount: 0,
        warningCount: 0,
        informationCount: 0,
        hintCount: 0,
        inputs: [{ id: 'src', path: 'in.xtf', model: 'M', format: 'xtf' }],
        outputs: [{ id: 'out', path: 'out.xtf', model: 'M', format: 'xtf' }],
        enumMaps: [],
        rules: [
          {
            id: 'r1',
            targetOutput: 'out',
            targetClass: 'M.A',
            sourceCount: 1,
            assignmentCount: 0,
            bagCount: 0,
            refCount: 0,
            status: 'ok'
          }
        ],
        diagnostics: [],
        coverageAvailable: true,
        coverageMessage: '',
        classCoverage: [],
        ruleCoverage: []
      };
    }
  };

  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    if (request === '../client') {
      return {
        getLanguageClient() {
          return clientMock;
        }
      };
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distPanelPath];
  const panelModule = require(distPanelPath);
  t.after(() => {
    delete require.cache[distPanelPath];
    Module._load = originalLoad;
  });

  await panelModule.openMappingOverview(
    { extensionUri: { fsPath: extensionRoot } },
    {
      appendLine(line) {
        outputLines.push(line);
      }
    }
  );

  assert.deepEqual(requested, [
    {
      method: 'ilimap/mappingSummary',
      params: {
        uri: 'file:///tmp/profile.ilimap'
      }
    }
  ]);
  assert.deepEqual(progressTitles, ['Opening ilimap mapping overview']);
  assert.equal(messages.length, 0);
  assert.equal(outputLines.length, 0);
  assert.equal(panels.length, 1);
  assert.equal(panels[0].options.enableScripts, true);
  assert.match(panels[0].webview.html, /ilimap Mapping Overview/);
  assert.match(panels[0].webview.html, /Profile/);

  await panels[0].messageCallback({ type: 'navigate', line: 7, character: 3 });
  assert.deepEqual(openedDocuments, ['file:///tmp/profile.ilimap']);
  assert.equal(selections.length, 1);
  assert.equal(selections[0].anchor.line, 7);
  assert.equal(selections[0].anchor.character, 3);
  assert.equal(revealedRanges.length, 1);
});

test('openMappingOverview reuses panel without registering duplicate message handlers', async (t) => {
  let createPanelCalls = 0;
  let handlerRegistrations = 0;
  const openedDocuments = [];
  let panel;

  function makeEditor(uriString, fsPath) {
    return {
      document: {
        languageId: 'ilimap',
        uri: {
          fsPath,
          toString() {
            return uriString;
          }
        }
      }
    };
  }

  const firstUri = 'file:///tmp/first.ilimap';
  const secondUri = 'file:///tmp/second.ilimap';

  const vscodeMock = {
    ViewColumn: {
      One: 1,
      Beside: 2
    },
    ProgressLocation: {
      Notification: 15
    },
    TextEditorRevealType: {
      InCenterIfOutsideViewport: 2
    },
    Position: class Position {
      constructor(line, character) {
        this.line = line;
        this.character = character;
      }
    },
    Range: class Range {
      constructor(start, end) {
        this.start = start;
        this.end = end;
      }
    },
    Selection: class Selection {
      constructor(anchor, active) {
        this.anchor = anchor;
        this.active = active;
      }
    },
    Uri: {
      parse(value) {
        return { value };
      }
    },
    workspace: {
      async openTextDocument(uri) {
        openedDocuments.push(uri.value);
        return { uri };
      }
    },
    window: {
      activeTextEditor: makeEditor(firstUri, '/tmp/first.ilimap'),
      createWebviewPanel(viewType, title, column, options) {
        createPanelCalls += 1;
        panel = {
          viewType,
          title,
          column,
          options,
          webview: {
            html: '',
            onDidReceiveMessage(callback) {
              handlerRegistrations += 1;
              panel.messageCallback = callback;
              return {
                dispose() {
                  panel.handlerDisposed = true;
                }
              };
            }
          },
          reveal(nextColumn) {
            panel.revealed = nextColumn;
          },
          onDidDispose(callback) {
            panel.disposeCallback = callback;
          }
        };
        return panel;
      },
      showInformationMessage() {},
      showErrorMessage() {},
      withProgress(options, task) {
        return task();
      },
      async showTextDocument(document, column, preserveFocus) {
        return {
          document,
          column,
          preserveFocus,
          set selection(value) {
            this._selection = value;
          },
          revealRange() {}
        };
      }
    }
  };

  const clientMock = {
    async sendRequest(method, params) {
      const mappingName = params.uri.includes('second') ? 'SecondProfile' : 'FirstProfile';
      return {
        available: true,
        message: '',
        mappingName,
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
        diagnostics: [],
        coverageAvailable: true,
        coverageMessage: '',
        classCoverage: [],
        ruleCoverage: []
      };
    }
  };

  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    if (request === '../client') {
      return {
        getLanguageClient() {
          return clientMock;
        }
      };
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distPanelPath];
  const panelModule = require(distPanelPath);
  t.after(() => {
    delete require.cache[distPanelPath];
    Module._load = originalLoad;
  });

  const context = { extensionUri: { fsPath: extensionRoot } };
  const outputChannel = { appendLine() {} };

  vscodeMock.window.activeTextEditor = makeEditor(firstUri, '/tmp/first.ilimap');
  await panelModule.openMappingOverview(context, outputChannel);

  vscodeMock.window.activeTextEditor = makeEditor(secondUri, '/tmp/second.ilimap');
  await panelModule.openMappingOverview(context, outputChannel);

  assert.equal(createPanelCalls, 1);
  assert.equal(handlerRegistrations, 1);
  assert.equal(panel.revealed, vscodeMock.ViewColumn.Beside);
  assert.match(panel.webview.html, /SecondProfile/);
  assert.doesNotMatch(panel.webview.html, /FirstProfile/);

  await panel.messageCallback({ type: 'navigate', line: 2, character: 4 });
  assert.equal(openedDocuments.length, 1);
  assert.equal(openedDocuments[0], secondUri);
});

test('navigateToLocation with end positions creates range selection', async (t) => {
  const selections = [];
  const revealedRanges = [];
  const openedDocuments = [];

  const vscodeMock = {
    ViewColumn: {
      One: 1,
      Beside: 2
    },
    ProgressLocation: {
      Notification: 15
    },
    TextEditorRevealType: {
      InCenterIfOutsideViewport: 2
    },
    Position: class Position {
      constructor(line, character) {
        this.line = line;
        this.character = character;
      }
    },
    Range: class Range {
      constructor(start, end) {
        this.start = start;
        this.end = end;
      }
    },
    Selection: class Selection {
      constructor(anchor, active) {
        this.anchor = anchor;
        this.active = active;
      }
    },
    Uri: {
      parse(value) {
        return { value };
      }
    },
    workspace: {
      async openTextDocument(uri) {
        openedDocuments.push(uri.value);
        return { uri };
      }
    },
    window: {
      activeTextEditor: {
        document: {
          languageId: 'ilimap',
          uri: {
            fsPath: '/tmp/profile.ilimap',
            toString() {
              return 'file:///tmp/profile.ilimap';
            }
          }
        }
      },
      createWebviewPanel(viewType, title, column, options) {
        const panel = {
          webview: {
            html: '',
            onDidReceiveMessage(callback) {
              panel.messageCallback = callback;
            }
          },
          reveal() {},
          onDidDispose() {}
        };
        return panel;
      },
      showInformationMessage() {},
      showErrorMessage() {},
      withProgress(options, task) {
        return task();
      },
      async showTextDocument(document, column, preserveFocus) {
        return {
          document,
          column,
          preserveFocus,
          set selection(value) {
            selections.push(value);
          },
          revealRange(range, revealType) {
            revealedRanges.push({ range, revealType });
          }
        };
      }
    }
  };

  const clientMock = {
    async sendRequest() {
      return {
        available: true,
        message: '',
        mappingName: 'Profile',
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
        diagnostics: [],
        coverageAvailable: true,
        coverageMessage: '',
        classCoverage: [],
        ruleCoverage: []
      };
    }
  };

  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    if (request === '../client') {
      return {
        getLanguageClient() {
          return clientMock;
        }
      };
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distPanelPath];
  const panelModule = require(distPanelPath);
  t.after(() => {
    delete require.cache[distPanelPath];
    Module._load = originalLoad;
  });

  let panel;
  vscodeMock.window.createWebviewPanel = (viewType, title, column, options) => {
    panel = {
      webview: {
        html: '',
        onDidReceiveMessage(callback) {
          panel.messageCallback = callback;
        }
      },
      reveal() {},
      onDidDispose() {}
    };
    return panel;
  };

  await panelModule.openMappingOverview(
    { extensionUri: { fsPath: extensionRoot } },
    { appendLine() {} }
  );

  await panel.messageCallback({
    type: 'navigateToLocation',
    location: { line: 3, character: 5, endLine: 3, endCharacter: 12 }
  });

  assert.equal(selections.length, 1);
  assert.equal(selections[0].anchor.line, 3);
  assert.equal(selections[0].anchor.character, 5);
  assert.equal(selections[0].active.line, 3);
  assert.equal(selections[0].active.character, 12);
  assert.equal(revealedRanges.length, 1);
  assert.equal(revealedRanges[0].range.start.line, 3);
  assert.equal(revealedRanges[0].range.end.line, 3);
  assert.equal(revealedRanges[0].range.end.character, 12);
});

test('legacy navigate message still works', async (t) => {
  const selections = [];
  const openedDocuments = [];

  const vscodeMock = {
    ViewColumn: { One: 1, Beside: 2 },
    ProgressLocation: { Notification: 15 },
    TextEditorRevealType: { InCenterIfOutsideViewport: 2 },
    Position: class Position {
      constructor(line, character) { this.line = line; this.character = character; }
    },
    Range: class Range {
      constructor(start, end) { this.start = start; this.end = end; }
    },
    Selection: class Selection {
      constructor(anchor, active) { this.anchor = anchor; this.active = active; }
    },
    Uri: { parse(value) { return { value }; } },
    workspace: {
      async openTextDocument(uri) {
        openedDocuments.push(uri.value);
        return { uri };
      }
    },
    window: {
      activeTextEditor: {
        document: {
          languageId: 'ilimap',
          uri: { fsPath: '/tmp/profile.ilimap', toString() { return 'file:///tmp/profile.ilimap'; } }
        }
      },
      createWebviewPanel() {
        const panel = {
          webview: {
            html: '',
            onDidReceiveMessage(callback) { panel.messageCallback = callback; }
          },
          reveal() {},
          onDidDispose() {}
        };
        return panel;
      },
      showInformationMessage() {},
      showErrorMessage() {},
      withProgress(options, task) { return task(); },
      async showTextDocument(document, column, preserveFocus) {
        return {
          document, column, preserveFocus,
          set selection(value) { selections.push(value); },
          revealRange() {}
        };
      }
    }
  };

  const clientMock = {
    async sendRequest() {
      return {
        available: true, message: '', mappingName: 'Profile',
        inputCount: 0, outputCount: 0, ruleCount: 0, enumMapCount: 0,
        bagCount: 0, refCount: 0, errorCount: 0, warningCount: 0,
        informationCount: 0, hintCount: 0,
        inputs: [], outputs: [], enumMaps: [], rules: [], diagnostics: [],
        coverageAvailable: true, coverageMessage: '', classCoverage: [], ruleCoverage: []
      };
    }
  };

  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') return vscodeMock;
    if (request === '../client') return { getLanguageClient() { return clientMock; } };
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distPanelPath];
  const panelModule = require(distPanelPath);
  t.after(() => { delete require.cache[distPanelPath]; Module._load = originalLoad; });

  let panel;
  vscodeMock.window.createWebviewPanel = () => {
    panel = {
      webview: {
        html: '',
        onDidReceiveMessage(callback) { panel.messageCallback = callback; }
      },
      reveal() {},
      onDidDispose() {}
    };
    return panel;
  };

  await panelModule.openMappingOverview(
    { extensionUri: { fsPath: extensionRoot } },
    { appendLine() {} }
  );

  await panel.messageCallback({ type: 'navigate', line: 42, character: 7 });

  assert.equal(selections.length, 1);
  assert.equal(selections[0].anchor.line, 42);
  assert.equal(selections[0].anchor.character, 7);
  assert.equal(selections[0].active.line, 42);
  assert.equal(selections[0].active.character, 7);
});

test('malformed messages are ignored', async (t) => {
  const outputLines = [];

  const vscodeMock = {
    ViewColumn: { One: 1, Beside: 2 },
    ProgressLocation: { Notification: 15 },
    TextEditorRevealType: { InCenterIfOutsideViewport: 2 },
    Position: class Position { constructor(line, character) { this.line = line; this.character = character; } },
    Range: class Range { constructor(start, end) { this.start = start; this.end = end; } },
    Selection: class Selection { constructor(anchor, active) { this.anchor = anchor; this.active = active; } },
    Uri: { parse(value) { return { value }; } },
    workspace: {
      async openTextDocument(uri) { return { uri }; }
    },
    window: {
      activeTextEditor: {
        document: {
          languageId: 'ilimap',
          uri: { fsPath: '/tmp/profile.ilimap', toString() { return 'file:///tmp/profile.ilimap'; } }
        }
      },
      createWebviewPanel() {
        const panel = {
          webview: {
            html: '',
            onDidReceiveMessage(callback) { panel.messageCallback = callback; }
          },
          reveal() {},
          onDidDispose() {}
        };
        return panel;
      },
      showInformationMessage() {},
      showErrorMessage() {},
      withProgress(options, task) { return task(); },
      async showTextDocument(document) {
        return { document, set selection(_value) {}, revealRange() {} };
      }
    }
  };

  const clientMock = {
    async sendRequest() {
      return {
        available: true, message: '', mappingName: 'Profile',
        inputCount: 0, outputCount: 0, ruleCount: 0, enumMapCount: 0,
        bagCount: 0, refCount: 0, errorCount: 0, warningCount: 0,
        informationCount: 0, hintCount: 0,
        inputs: [], outputs: [], enumMaps: [], rules: [], diagnostics: [],
        coverageAvailable: true, coverageMessage: '', classCoverage: [], ruleCoverage: []
      };
    }
  };

  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') return vscodeMock;
    if (request === '../client') return { getLanguageClient() { return clientMock; } };
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distPanelPath];
  const panelModule = require(distPanelPath);
  t.after(() => { delete require.cache[distPanelPath]; Module._load = originalLoad; });

  let panel;
  vscodeMock.window.createWebviewPanel = () => {
    panel = {
      webview: {
        html: '',
        onDidReceiveMessage(callback) { panel.messageCallback = callback; }
      },
      reveal() {},
      onDidDispose() {}
    };
    return panel;
  };

  const outputChannel = {
    appendLine(line) { outputLines.push(line); }
  };

  await panelModule.openMappingOverview(
    { extensionUri: { fsPath: extensionRoot } },
    outputChannel
  );

  const beforeLineCount = outputLines.length;

  await panel.messageCallback(null);
  await panel.messageCallback(undefined);
  await panel.messageCallback({});
  await panel.messageCallback({ type: 'navigate' });
  await panel.messageCallback({ type: 'navigate', line: 'not-a-number', character: 0 });
  await panel.messageCallback({ type: 'navigate', line: 0, character: -1 });
  await panel.messageCallback({ type: 'navigate', line: -5, character: 0 });
  await panel.messageCallback({ type: 'unknown', data: 1 });
  await panel.messageCallback({ type: 'navigateToLocation', location: null });
  await panel.messageCallback({ type: 'navigateToLocation', location: {} });
  await panel.messageCallback({ type: 'navigateToLocation', location: { line: -1, character: 0 } });

  assert.equal(outputLines.length, beforeLineCount);
});

test('navigateToLocation without end positions creates point selection', async (t) => {
  const selections = [];

  const vscodeMock = {
    ViewColumn: { One: 1, Beside: 2 },
    ProgressLocation: { Notification: 15 },
    TextEditorRevealType: { InCenterIfOutsideViewport: 2 },
    Position: class Position {
      constructor(line, character) { this.line = line; this.character = character; }
    },
    Range: class Range {
      constructor(start, end) { this.start = start; this.end = end; }
    },
    Selection: class Selection {
      constructor(anchor, active) { this.anchor = anchor; this.active = active; }
    },
    Uri: { parse(value) { return { value }; } },
    workspace: {
      async openTextDocument(uri) { return { uri }; }
    },
    window: {
      activeTextEditor: {
        document: {
          languageId: 'ilimap',
          uri: { fsPath: '/tmp/profile.ilimap', toString() { return 'file:///tmp/profile.ilimap'; } }
        }
      },
      createWebviewPanel() {
        const panel = {
          webview: {
            html: '',
            onDidReceiveMessage(callback) { panel.messageCallback = callback; }
          },
          reveal() {},
          onDidDispose() {}
        };
        return panel;
      },
      showInformationMessage() {},
      showErrorMessage() {},
      withProgress(options, task) { return task(); },
      async showTextDocument(document) {
        return {
          document,
          set selection(value) { selections.push(value); },
          revealRange() {}
        };
      }
    }
  };

  const clientMock = {
    async sendRequest() {
      return {
        available: true, message: '', mappingName: 'Profile',
        inputCount: 0, outputCount: 0, ruleCount: 0, enumMapCount: 0,
        bagCount: 0, refCount: 0, errorCount: 0, warningCount: 0,
        informationCount: 0, hintCount: 0,
        inputs: [], outputs: [], enumMaps: [], rules: [], diagnostics: [],
        coverageAvailable: true, coverageMessage: '', classCoverage: [], ruleCoverage: []
      };
    }
  };

  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') return vscodeMock;
    if (request === '../client') return { getLanguageClient() { return clientMock; } };
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distPanelPath];
  const panelModule = require(distPanelPath);
  t.after(() => { delete require.cache[distPanelPath]; Module._load = originalLoad; });

  let panel;
  vscodeMock.window.createWebviewPanel = () => {
    panel = {
      webview: {
        html: '',
        onDidReceiveMessage(callback) { panel.messageCallback = callback; }
      },
      reveal() {},
      onDidDispose() {}
    };
    return panel;
  };

  await panelModule.openMappingOverview(
    { extensionUri: { fsPath: extensionRoot } },
    { appendLine() {} }
  );

  await panel.messageCallback({
    type: 'navigateToLocation',
    location: { line: 7, character: 3 }
  });

  assert.equal(selections.length, 1);
  assert.equal(selections[0].anchor.line, 7);
  assert.equal(selections[0].anchor.character, 3);
  assert.equal(selections[0].active.line, 7);
  assert.equal(selections[0].active.character, 3);
});

const CHANGE_DEBOUNCE_MS = 500;

test('manual refresh message triggers a new mappingSummary request', async (t) => {
  const harness = makeHarness();
  const panelModule = loadPanel(t, harness);

  await panelModule.openMappingOverview(context(), harness.outputChannel);
  assert.equal(harness.refs.requested.length, 1);

  await harness.refs.panel.messageCallback({ type: 'refresh' });

  assert.equal(harness.refs.requested.length, 2);
  assert.deepEqual(harness.refs.requested[1], {
    method: 'ilimap/mappingSummary',
    params: { uri: 'file:///tmp/profile.ilimap' }
  });
  assert.match(harness.refs.panel.webview.html, /Profile/);
  assert.match(harness.refs.panel.webview.html, /data-action="refresh"/);
});

test('saving the bound document triggers a refresh', async (t) => {
  const harness = makeHarness();
  const panelModule = loadPanel(t, harness);

  await panelModule.openMappingOverview(context(), harness.outputChannel);
  assert.equal(harness.refs.saveHandlers.length, 1);

  await harness.refs.saveHandlers[0](document('file:///tmp/profile.ilimap', '/tmp/profile.ilimap', 2));
  await flush();

  assert.equal(harness.refs.requested.length, 2);
  assert.equal(harness.refs.requested[1].params.uri, 'file:///tmp/profile.ilimap');
});

test('saving an unrelated document does not trigger a refresh', async (t) => {
  const harness = makeHarness();
  const panelModule = loadPanel(t, harness);

  await panelModule.openMappingOverview(context(), harness.outputChannel);

  await harness.refs.saveHandlers[0](document('file:///tmp/other.ilimap', '/tmp/other.ilimap', 1));
  await harness.refs.saveHandlers[0]({
    languageId: 'plaintext',
    version: 1,
    uri: { fsPath: '/tmp/profile.txt', toString() { return 'file:///tmp/profile.txt'; } }
  });
  await flush();

  assert.equal(harness.refs.requested.length, 1);
});

test('multiple rapid changes debounce into a single refresh', async (t) => {
  const harness = makeHarness();
  const panelModule = loadPanel(t, harness);

  await panelModule.openMappingOverview(context(), harness.outputChannel);
  assert.equal(harness.refs.changeHandlers.length, 1);

  harness.refs.changeHandlers[0]({ document: document('file:///tmp/profile.ilimap', '/tmp/profile.ilimap', 2) });
  harness.refs.changeHandlers[0]({ document: document('file:///tmp/profile.ilimap', '/tmp/profile.ilimap', 3) });
  harness.refs.changeHandlers[0]({ document: document('file:///tmp/profile.ilimap', '/tmp/profile.ilimap', 4) });

  assert.equal(harness.refs.requested.length, 1);

  await delay(CHANGE_DEBOUNCE_MS + 200);
  await flush();

  assert.equal(harness.refs.requested.length, 2);
});

test('changes to an unrelated document do not schedule a refresh', async (t) => {
  const harness = makeHarness();
  const panelModule = loadPanel(t, harness);

  await panelModule.openMappingOverview(context(), harness.outputChannel);

  harness.refs.changeHandlers[0]({ document: document('file:///tmp/other.ilimap', '/tmp/other.ilimap', 2) });

  await delay(CHANGE_DEBOUNCE_MS + 200);
  await flush();

  assert.equal(harness.refs.requested.length, 1);
});

test('failed refresh logs an error and shows an error banner without losing the summary', async (t) => {
  let calls = 0;
  const harness = makeHarness({
    respond() {
      calls += 1;
      if (calls >= 2) {
        throw new Error('boom');
      }
      return baseSummary('Profile');
    }
  });
  const panelModule = loadPanel(t, harness);

  await panelModule.openMappingOverview(context(), harness.outputChannel);
  await harness.refs.panel.messageCallback({ type: 'refresh' });
  await flush();

  assert.ok(
    harness.refs.outputLines.some(
      line => line.includes('Failed to refresh ilimap mapping overview') && line.includes('boom')
    )
  );
  assert.match(harness.refs.panel.webview.html, /Failed to refresh: boom/);
  assert.match(harness.refs.panel.webview.html, /Profile/);
});

function context() {
  return { extensionUri: { fsPath: extensionRoot } };
}

function document(uriString, fsPath, version) {
  return {
    languageId: 'ilimap',
    version,
    uri: {
      fsPath,
      toString() {
        return uriString;
      }
    }
  };
}

function flush() {
  return new Promise(resolve => setImmediate(resolve));
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function baseSummary(name) {
  return {
    available: true,
    message: '',
    mappingName: name,
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
    diagnostics: [],
    coverageAvailable: true,
    coverageMessage: '',
    classCoverage: [],
    ruleCoverage: []
  };
}

function makeHarness({ activeUri = 'file:///tmp/profile.ilimap', activeFsPath = '/tmp/profile.ilimap', respond } = {}) {
  const refs = {
    requested: [],
    outputLines: [],
    saveHandlers: [],
    changeHandlers: [],
    panel: null
  };

  const clientMock = {
    async sendRequest(method, params) {
      refs.requested.push({ method, params });
      if (respond) {
        return respond(method, params, refs);
      }
      return baseSummary('Profile');
    }
  };

  const vscodeMock = {
    ViewColumn: { One: 1, Beside: 2 },
    ProgressLocation: { Notification: 15 },
    TextEditorRevealType: { InCenterIfOutsideViewport: 2 },
    Position: class Position {
      constructor(line, character) { this.line = line; this.character = character; }
    },
    Range: class Range {
      constructor(start, end) { this.start = start; this.end = end; }
    },
    Selection: class Selection {
      constructor(anchor, active) { this.anchor = anchor; this.active = active; }
    },
    Uri: { parse(value) { return { value }; } },
    workspace: {
      async openTextDocument(uri) { return { uri }; },
      onDidSaveTextDocument(callback) {
        refs.saveHandlers.push(callback);
        return { dispose() {} };
      },
      onDidChangeTextDocument(callback) {
        refs.changeHandlers.push(callback);
        return { dispose() {} };
      }
    },
    window: {
      activeTextEditor: {
        document: document(activeUri, activeFsPath, 1)
      },
      createWebviewPanel(viewType, title, column, options) {
        const panel = {
          viewType,
          title,
          column,
          options,
          webview: {
            html: '',
            onDidReceiveMessage(callback) {
              panel.messageCallback = callback;
              return { dispose() {} };
            }
          },
          reveal(nextColumn) { panel.revealed = nextColumn; },
          onDidDispose(callback) { panel.disposeCallback = callback; }
        };
        refs.panel = panel;
        return panel;
      },
      showInformationMessage() {},
      showErrorMessage() {},
      withProgress(options, task) { return task(); },
      async showTextDocument(doc, column, preserveFocus) {
        return {
          document: doc,
          column,
          preserveFocus,
          set selection(_value) {},
          revealRange() {}
        };
      }
    }
  };

  const outputChannel = {
    appendLine(line) { refs.outputLines.push(line); }
  };

  return { vscodeMock, clientMock, outputChannel, refs };
}

function loadPanel(t, harness) {
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return harness.vscodeMock;
    }
    if (request === '../client') {
      return {
        getLanguageClient() {
          return harness.clientMock;
        }
      };
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distPanelPath];
  const panelModule = require(distPanelPath);
  t.after(() => {
    delete require.cache[distPanelPath];
    Module._load = originalLoad;
  });
  return panelModule;
}
