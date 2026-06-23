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
  assert.equal(messages.length, 0);
  assert.equal(outputLines.length, 0);
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

function loadCommandsModule({ client, vscodeOverrides }) {
  const registered = new Map();
  const vscodeMock = {
    commands: {
      registerCommand(command, callback) {
        registered.set(command, callback);
        return { dispose() {} };
      },
      executeCommand(command) {
        return vscodeOverrides.commands.executeCommand(command);
      }
    },
    window: vscodeOverrides.window
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
    if (request === './webview/mappingOverviewPanel') {
      return {
        async openMappingOverview() {}
      };
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distCommandsPath];
  const commandsModule = require(distCommandsPath);
  return {
    commandsModule,
    registered,
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
