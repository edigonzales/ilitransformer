const assert = require('node:assert/strict');
const fs = require('node:fs');
const Module = require('node:module');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');

const extensionRoot = path.resolve(__dirname, '..');
const distConfigurationPath = path.join(extensionRoot, 'dist', 'configuration.js');

function loadConfigurationModule(vscodeMock) {
  const originalLoad = Module._load;
  Module._load = function mockedLoad(request, parent, isMain) {
    if (request === 'vscode') {
      return vscodeMock;
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  delete require.cache[distConfigurationPath];
  const configurationModule = require(distConfigurationPath);

  return {
    configurationModule,
    restore() {
      delete require.cache[distConfigurationPath];
      Module._load = originalLoad;
    }
  };
}

test('resolveJavaRuntime prefers an explicitly configured executable', (t) => {
  const loaded = loadConfigurationModule({
    window: {
      showErrorMessage() {}
    }
  });
  t.after(() => loaded.restore());

  const runtime = loaded.configurationModule.resolveJavaRuntime(
    {
      asAbsolutePath(relativePath) {
        return path.join(extensionRoot, relativePath);
      }
    },
    ' /custom/java '
  );

  assert.deepEqual(runtime, {
    command: '/custom/java',
    source: 'configured'
  });
});

test('resolveJavaRuntime uses a bundled runtime when present', (t) => {
  const workspace = fs.mkdtempSync(path.join(os.tmpdir(), 'ilimap-config-'));
  const loaded = loadConfigurationModule({
    window: {
      showErrorMessage() {}
    }
  });
  t.after(() => loaded.restore());

  const platformId = loaded.configurationModule.runtimePlatformId(process.platform, process.arch);
  if (!platformId) {
    t.skip(`unsupported platform for this test host: ${process.platform}/${process.arch}`);
    return;
  }

  const executable = process.platform === 'win32' ? 'java.exe' : 'java';
  const bundledJava = path.join(workspace, 'server', 'jre', platformId, 'bin', executable);
  fs.mkdirSync(path.dirname(bundledJava), { recursive: true });
  fs.writeFileSync(bundledJava, '');

  const runtime = loaded.configurationModule.resolveJavaRuntime(
    {
      asAbsolutePath(relativePath) {
        return path.join(workspace, relativePath);
      }
    },
    ''
  );

  assert.deepEqual(runtime, {
    command: bundledJava,
    source: 'bundled',
    platformId
  });
});

test('resolveJavaRuntime falls back to system java when no bundle exists', (t) => {
  const workspace = fs.mkdtempSync(path.join(os.tmpdir(), 'ilimap-config-'));
  const loaded = loadConfigurationModule({
    window: {
      showErrorMessage() {}
    }
  });
  t.after(() => loaded.restore());

  const runtime = loaded.configurationModule.resolveJavaRuntime(
    {
      asAbsolutePath(relativePath) {
        return path.join(workspace, relativePath);
      }
    },
    undefined
  );

  assert.deepEqual(runtime, {
    command: 'java',
    source: 'system'
  });
});
