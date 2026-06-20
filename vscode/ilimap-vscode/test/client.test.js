const assert = require('node:assert/strict');
const Module = require('node:module');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const distClientPath = path.join(extensionRoot, 'dist', 'client.js');

test('restartLanguageClient stops the current client and starts a new one', async (t) => {
  const configValues = new Map([
    ['server.jarPath', '/tmp/ilimap-lsp-all.jar'],
    ['java.path', '/usr/bin/java'],
    ['server.jvmArgs', ['-Xmx1g', '-Dfoo=bar']]
  ]);
  const outputLines = [];
  const clients = [];

  class FakeLanguageClient {
    constructor(id, name, serverOptions, clientOptions) {
      this.id = id;
      this.name = name;
      this.serverOptions = serverOptions;
      this.clientOptions = clientOptions;
      this.started = false;
      this.stopped = false;
      clients.push(this);
    }

    async start() {
      this.started = true;
    }

    async stop() {
      this.stopped = true;
    }
  }

  const vscodeMock = {
    workspace: {
      getConfiguration(section) {
        assert.equal(section, 'ilimap');
        return {
          get(key) {
            return configValues.get(key);
          }
        };
      },
      createFileSystemWatcher(pattern) {
        assert.equal(pattern, '**/*.ilimap');
        return {
          dispose() {}
        };
      }
    },
    window: {
      showErrorMessage() {}
    }
  };

  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    if (request === 'vscode-languageclient/node') {
      return {
        LanguageClient: FakeLanguageClient
      };
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distClientPath];
  const clientModule = require(distClientPath);
  t.after(async () => {
    await clientModule.stopLanguageClient().catch(() => undefined);
    delete require.cache[distClientPath];
    Module._load = originalLoad;
  });

  const context = {
    asAbsolutePath(relativePath) {
      return path.join(extensionRoot, relativePath);
    }
  };
  const outputChannel = {
    appendLine(line) {
      outputLines.push(line);
    }
  };

  await clientModule.startLanguageClient(context, outputChannel);
  await clientModule.startLanguageClient(context, outputChannel);

  assert.equal(clients.length, 1);
  assert.equal(clients[0].serverOptions.command, '/usr/bin/java');
  assert.deepEqual(clients[0].serverOptions.args, [
    '-Xmx1g',
    '-Dfoo=bar',
    '-jar',
    '/tmp/ilimap-lsp-all.jar'
  ]);
  assert.deepEqual(clients[0].clientOptions.documentSelector, [{ scheme: 'file', language: 'ilimap' }]);

  await clientModule.restartLanguageClient(context, outputChannel);

  assert.equal(clients.length, 2);
  assert.equal(clients[0].stopped, true);
  assert.equal(clients[1].started, true);
  assert.equal(clientModule.getLanguageClient(), clients[1]);
  assert.ok(outputLines.includes('Restarting ILIMAP language server.'));
  assert.ok(outputLines.includes('ILIMAP language server restart complete.'));
});
