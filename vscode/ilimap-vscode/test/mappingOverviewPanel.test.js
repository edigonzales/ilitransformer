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

  const vscodeMock = {
    ViewColumn: {
      Beside: 2
    },
    ProgressLocation: {
      Notification: 15
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
            html: ''
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
        diagnostics: []
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
  assert.equal(panels[0].options.enableScripts, false);
  assert.match(panels[0].webview.html, /ilimap Mapping Overview/);
  assert.match(panels[0].webview.html, /Profile/);
});
