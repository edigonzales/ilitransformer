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
