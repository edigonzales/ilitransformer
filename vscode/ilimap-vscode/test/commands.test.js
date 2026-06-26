const assert = require('node:assert/strict');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const distCommandsPath = path.join(extensionRoot, 'dist', 'commands.js');

test('validate command requests validation for active ilimap document and focuses problems', async (t) => {
  const requested = [];
  const executed = [];
  const messages = [];
  const outputLines = [];
  const progressTitles = [];
  const { commandsModule, registered, restore } = loadCommandsModule({
    client: {
      async sendRequest(method, params) {
        requested.push({ method, params });
        return { available: true, message: '', diagnosticCount: 0 };
      }
    },
    vscodeOverrides: {
      commands: {
        executeCommand(command) {
          executed.push(command);
          return Promise.resolve();
        }
      },
      window: {
        activeTextEditor: {
          document: ilimapDocument()
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
    }
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel(outputLines));
  await registered.get('ilimap.validate')();

  assert.deepEqual(requested, [
    {
      method: 'ilimap/validateMapping',
      params: {
        uri: 'file:///tmp/profile.ilimap',
        text: 'mapping v2 {}',
        version: 7
      }
    }
  ]);
  assert.deepEqual(executed, ['workbench.action.problems.focus']);
  assert.deepEqual(progressTitles, ['Validating ilimap mapping']);
  assert.equal(messages.length, 0);
  assert.deepEqual(outputLines, ['ilimap validation completed with 0 diagnostics.']);
});

test('validate command reports unavailable validation result', async (t) => {
  const requested = [];
  const executed = [];
  const messages = [];
  const outputLines = [];
  const { commandsModule, registered, restore } = loadCommandsModule({
    client: {
      async sendRequest(method, params) {
        requested.push({ method, params });
        return { available: false, message: 'No open ILIMAP document.', diagnosticCount: 0 };
      }
    },
    vscodeOverrides: {
      commands: {
        executeCommand(command) {
          executed.push(command);
          return Promise.resolve();
        }
      },
      window: {
        activeTextEditor: {
          document: ilimapDocument()
        },
        showInformationMessage(message) {
          messages.push({ kind: 'info', message });
        },
        showErrorMessage(message) {
          messages.push({ kind: 'error', message });
        },
        withProgress(_options, task) {
          return task();
        }
      }
    }
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel(outputLines));
  await registered.get('ilimap.validate')();

  assert.equal(requested.length, 1);
  assert.deepEqual(executed, []);
  assert.deepEqual(messages, [{ kind: 'error', message: 'No open ILIMAP document.' }]);
  assert.deepEqual(outputLines, ['ilimap validation unavailable: No open ILIMAP document.']);
});

test('validate command shows information message without active ilimap editor', async (t) => {
  const executed = [];
  const messages = [];
  const { commandsModule, registered, restore } = loadCommandsModule({
    client: {
      async sendRequest() {
        throw new Error('unexpected request');
      }
    },
    vscodeOverrides: {
      commands: {
        executeCommand(command) {
          executed.push(command);
          return Promise.resolve();
        }
      },
      window: {
        activeTextEditor: undefined,
        showInformationMessage(message) {
          messages.push({ kind: 'info', message });
        },
        showErrorMessage(message) {
          messages.push({ kind: 'error', message });
        },
        withProgress(_options, task) {
          return task();
        }
      }
    }
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel([]));
  await registered.get('ilimap.validate')();

  assert.deepEqual(executed, []);
  assert.deepEqual(messages, [
    {
      kind: 'info',
      message: 'Open an .ilimap document before validating the ilimap mapping.'
    }
  ]);
});

test('validate command shows error message without language client', async (t) => {
  const executed = [];
  const messages = [];
  const { commandsModule, registered, restore } = loadCommandsModule({
    client: undefined,
    vscodeOverrides: {
      commands: {
        executeCommand(command) {
          executed.push(command);
          return Promise.resolve();
        }
      },
      window: {
        activeTextEditor: {
          document: ilimapDocument()
        },
        showInformationMessage(message) {
          messages.push({ kind: 'info', message });
        },
        showErrorMessage(message) {
          messages.push({ kind: 'error', message });
        },
        withProgress(_options, task) {
          return task();
        }
      }
    }
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel([]));
  await registered.get('ilimap.validate')();

  assert.deepEqual(executed, []);
  assert.deepEqual(messages, [
    {
      kind: 'error',
      message: 'ilimap language server is not running.'
    }
  ]);
});

test('showRuleInOverview command forwards uri and ruleId to the panel', async (t) => {
  const { commandsModule, registered, panelCalls, restore } = loadCommandsModule({
    client: undefined,
    vscodeOverrides: {
      commands: { executeCommand() { return Promise.resolve(); } },
      window: { showInformationMessage() {}, showErrorMessage() {} }
    }
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel([]));
  await registered.get('ilimap.showRuleInOverview')('file:///profile.ilimap', 'r1');

  assert.equal(panelCalls.length, 1);
  assert.equal(panelCalls[0].fn, 'showRuleInOverview');
  assert.equal(panelCalls[0].args[2], 'file:///profile.ilimap');
  assert.equal(panelCalls[0].args[3], 'r1');
});

test('showRuleCoverage command forwards uri and ruleId to the panel', async (t) => {
  const { commandsModule, registered, panelCalls, restore } = loadCommandsModule({
    client: undefined,
    vscodeOverrides: {
      commands: { executeCommand() { return Promise.resolve(); } },
      window: { showInformationMessage() {}, showErrorMessage() {} }
    }
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel([]));
  await registered.get('ilimap.showRuleCoverage')('file:///profile.ilimap', 'r2');

  assert.equal(panelCalls.length, 1);
  assert.equal(panelCalls[0].fn, 'showRuleCoverage');
  assert.equal(panelCalls[0].args[2], 'file:///profile.ilimap');
  assert.equal(panelCalls[0].args[3], 'r2');
});

test('export command requests summary and writes markdown file', async (t) => {
  const writtenFiles = [];
  const messages = [];
  const saveDialogOptions = [];
  const executed = [];
  const outputLines = [];
  const progressTitles = [];

  let reportMarkdown = '';
  let markdownSummary = null;

  const vscodeOverrides = {
    Uri: {
      parse(value) {
        return { value };
      },
      file(path) {
        return { fsPath: path, value: `file:///${path}` };
      }
    },
    ProgressLocation: {
      Notification: 15
    },
    workspace: {
      fs: {
        async writeFile(uri, buffer) {
          writtenFiles.push({ uri, content: buffer.toString('utf8') });
        }
      }
    },
    window: {
      activeTextEditor: {
        document: ilimapDocument()
      },
      showInformationMessage(message, ...actions) {
        messages.push({ kind: 'info', message, actions });
        return actions[0] || undefined;
      },
      showErrorMessage(message) {
        messages.push({ kind: 'error', message });
      },
      showSaveDialog(options) {
        saveDialogOptions.push(options);
        return { fsPath: '/tmp/export.md', value: 'file:///tmp/export.md' };
      },
      withProgress(options, task) {
        progressTitles.push(options.title);
        return task();
      }
    },
    commands: {
      executeCommand(command) {
        executed.push(command);
        return Promise.resolve();
      }
    }
  };

  const { commandsModule, registered, restore } = loadCommandsModule({
    client: {
      async sendRequest(method, params) {
        if (method === 'ilimap/mappingSummary') {
          const s = exportSummary();
          markdownSummary = s;
          reportMarkdown = `# Report: ${s.mappingName}`;
          return s;
        }
        return {};
      }
    },
    vscodeOverrides,
    reporterMarkdown(markdown) {
      reportMarkdown = markdown;
    }
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel(outputLines));
  await registered.get('ilimap.exportMappingReport')();

  assert.ok(markdownSummary, 'should have requested mapping summary');
  assert.equal(progressTitles.length, 1);
  assert.match(progressTitles[0], /Exporting/i);
  assert.equal(saveDialogOptions.length, 1);
  assert.equal(saveDialogOptions[0].filters.Markdown[0], 'md');
  assert.match(saveDialogOptions[0].defaultUri.fsPath, /\.mapping-report\.md$/);
  assert.equal(writtenFiles.length, 1);
  assert.match(writtenFiles[0].content, /Report/);
  const infoMessages = messages.filter(m => m.kind === 'info');
  assert.ok(infoMessages.length > 0);
  assert.match(infoMessages[0].message, /saved to/);
});

test('export command shows error for unavailable summary', async (t) => {
  const writtenFiles = [];
  const messages = [];
  const outputLines = [];

  const vscodeOverrides = {
    Uri: {
      parse(value) { return { value }; },
      file(path) { return { fsPath: path }; }
    },
    ProgressLocation: { Notification: 15 },
    workspace: {
      fs: {
        async writeFile(uri, buffer) {
          writtenFiles.push({ uri, content: buffer.toString('utf8') });
        }
      }
    },
    window: {
      activeTextEditor: {
        document: ilimapDocument()
      },
      showInformationMessage(message) {
        messages.push({ kind: 'info', message });
      },
      showErrorMessage(message) {
        messages.push({ kind: 'error', message });
      },
      showSaveDialog() {
        return { fsPath: '/tmp/export.md' };
      },
      withProgress(options, task) {
        return task();
      }
    },
    commands: {
      executeCommand() { return Promise.resolve(); }
    }
  };

  const { commandsModule, registered, restore } = loadCommandsModule({
    client: {
      async sendRequest() {
        return {
          available: false,
          message: 'No mapping analysis available.',
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
    },
    vscodeOverrides
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel(outputLines));
  await registered.get('ilimap.exportMappingReport')();

  assert.equal(writtenFiles.length, 0);
  const errorMessages = messages.filter(m => m.kind === 'error');
  assert.ok(errorMessages.length > 0);
  assert.match(errorMessages[0].message, /No mapping analysis available/);
});

test('export command shows error when no active editor', async (t) => {
  const messages = [];
  const outputLines = [];

  const vscodeOverrides = {
    Uri: { parse() {}, file() {} },
    ProgressLocation: { Notification: 15 },
    window: {
      activeTextEditor: undefined,
      showInformationMessage(message) {
        messages.push({ kind: 'info', message });
      },
      showErrorMessage(message) {
        messages.push({ kind: 'error', message });
      }
    },
    commands: { executeCommand() { return Promise.resolve(); } }
  };

  const { commandsModule, registered, restore } = loadCommandsModule({
    client: {},
    vscodeOverrides
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel(outputLines));
  await registered.get('ilimap.exportMappingReport')();

  const infoMessages = messages.filter(m => m.kind === 'info');
  assert.ok(infoMessages.length > 0);
  assert.match(infoMessages[0].message, /Open an \.ilimap document/);
});

test('export command shows error when language server not running', async (t) => {
  const messages = [];
  const outputLines = [];

  const vscodeOverrides = {
    Uri: { parse() {}, file() {} },
    ProgressLocation: { Notification: 15 },
    window: {
      activeTextEditor: {
        document: ilimapDocument()
      },
      showInformationMessage(message) {
        messages.push({ kind: 'info', message });
      },
      showErrorMessage(message) {
        messages.push({ kind: 'error', message });
      }
    },
    commands: { executeCommand() { return Promise.resolve(); } }
  };

  const { commandsModule, registered, restore } = loadCommandsModule({
    client: undefined,
    vscodeOverrides
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel(outputLines));
  await registered.get('ilimap.exportMappingReport')();

  const errorMessages = messages.filter(m => m.kind === 'error');
  assert.ok(errorMessages.length > 0);
  assert.match(errorMessages[0].message, /not running/);
});

test('export command handles write file error', async (t) => {
  const messages = [];
  const outputLines = [];

  const vscodeOverrides = {
    Uri: {
      parse(value) { return { value }; },
      file(path) { return { fsPath: path }; }
    },
    ProgressLocation: { Notification: 15 },
    workspace: {
      fs: {
        async writeFile() {
          throw new Error('disk full');
        }
      }
    },
    window: {
      activeTextEditor: {
        document: ilimapDocument()
      },
      showInformationMessage(message) {
        messages.push({ kind: 'info', message });
      },
      showErrorMessage(message) {
        messages.push({ kind: 'error', message });
      },
      showSaveDialog() {
        return { fsPath: '/tmp/export.md' };
      },
      withProgress(options, task) {
        return task();
      }
    },
    commands: {
      executeCommand() { return Promise.resolve(); }
    }
  };

  const { commandsModule, registered, restore } = loadCommandsModule({
    client: {
      async sendRequest() {
        return exportSummary();
      }
    },
    vscodeOverrides
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel(outputLines));
  await registered.get('ilimap.exportMappingReport')();

  assert.match(outputLines[0] || '', /disk full/);
  const errorMessages = messages.filter(m => m.kind === 'error');
  assert.ok(errorMessages.length > 0);
  assert.match(errorMessages[0].message, /Failed to write/);
});

test('export command handles server request error', async (t) => {
  const messages = [];
  const outputLines = [];

  const vscodeOverrides = {
    Uri: { parse() {}, file() {} },
    ProgressLocation: { Notification: 15 },
    window: {
      activeTextEditor: {
        document: ilimapDocument()
      },
      showInformationMessage(message) {
        messages.push({ kind: 'info', message });
      },
      showErrorMessage(message) {
        messages.push({ kind: 'error', message });
      },
      withProgress(options, task) {
        return task();
      }
    },
    commands: { executeCommand() { return Promise.resolve(); } }
  };

  const { commandsModule, registered, restore } = loadCommandsModule({
    client: {
      async sendRequest() {
        throw new Error('connection lost');
      }
    },
    vscodeOverrides
  });
  t.after(restore);

  commandsModule.registerCommands(context(), outputChannel(outputLines));
  await registered.get('ilimap.exportMappingReport')();

  assert.match(outputLines[0] || '', /connection lost/);
  const errorMessages = messages.filter(m => m.kind === 'error');
  assert.ok(errorMessages.length > 0);
});

function loadCommandsModule({ client, vscodeOverrides, reporterMarkdown }) {
  const registered = new Map();
  const panelCalls = [];
  const defaultCommands = {
    registerCommand(command, callback) {
      registered.set(command, callback);
      return { dispose() {} };
    },
    executeCommand() { return Promise.resolve(); }
  };
  const vscodeMock = {
    ...vscodeOverrides,
    ProgressLocation: vscodeOverrides.ProgressLocation || { Notification: 15 },
    commands: {
      ...defaultCommands,
      ...vscodeOverrides.commands
    }
  };

  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    if (request === './client') {
      return {
        getLanguageClient() {
          return client;
        },
        async restartLanguageClient() {}
      };
    }
    if (request === './overview/mappingOverviewReporter') {
      return {
        renderMappingReportMarkdown(summary, options) {
          if (typeof reporterMarkdown === 'function') {
            reporterMarkdown(`# Report: ${summary.mappingName || 'mapping'}`);
          }
          return `# Report: ${summary.mappingName || 'mapping'}\n\n## Summary\n`;
        }
      };
    }
    if (request === './webview/mappingOverviewMessages') {
      return {
        mappingSummaryRequest: 'ilimap/mappingSummary',
        ruleDetailRequest: 'ilimap/ruleDetail',
        type: {}
      };
    }
    if (request === './webview/mappingOverviewPanel') {
      return {
        async openMappingOverview(...args) {
          panelCalls.push({ fn: 'openMappingOverview', args });
        },
        async refreshOpenMappingOverview(...args) {
          panelCalls.push({ fn: 'refreshOpenMappingOverview', args });
        },
        async showRuleInOverview(...args) {
          panelCalls.push({ fn: 'showRuleInOverview', args });
        },
        async showRuleCoverage(...args) {
          panelCalls.push({ fn: 'showRuleCoverage', args });
        }
      };
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distCommandsPath];
  const commandsModule = require(distCommandsPath);
  return {
    commandsModule,
    registered,
    panelCalls,
    restore() {
      delete require.cache[distCommandsPath];
      Module._load = originalLoad;
    }
  };
}

function context() {
  return { subscriptions: [] };
}

function outputChannel(lines) {
  return {
    appendLine(line) {
      lines.push(line);
    }
  };
}

function ilimapDocument() {
  return {
    languageId: 'ilimap',
    version: 7,
    uri: {
      fsPath: '/tmp/profile.ilimap',
      toString() {
        return 'file:///tmp/profile.ilimap';
      }
    },
    getText() {
      return 'mapping v2 {}';
    }
  };
}

function exportSummary() {
  return {
    available: true,
    message: '',
    mappingName: 'TestProfile',
    inputCount: 2,
    outputCount: 1,
    ruleCount: 1,
    enumMapCount: 0,
    bagCount: 0,
    refCount: 0,
    errorCount: 0,
    warningCount: 0,
    informationCount: 0,
    hintCount: 0,
    inputs: [
      { id: 'id_1', path: 'in1.xtf', model: 'ModelA', format: 'xtf' }
    ],
    outputs: [
      { id: 'out_1', path: 'out.xtf', model: 'TargetModel', format: 'xtf' }
    ],
    enumMaps: [],
    rules: [
      {
        id: 'r1',
        targetOutput: 'out_1',
        targetClass: 'TargetModel.A',
        sourceCount: 1,
        assignmentCount: 2,
        bagCount: 0,
        refCount: 0,
        status: 'ok'
      }
    ],
    diagnostics: []
  };
}
