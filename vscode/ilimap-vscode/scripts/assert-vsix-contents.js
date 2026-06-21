#!/usr/bin/env node

const { execFileSync } = require('node:child_process');

const { validateVsixEntries } = require('./vsix-contents');

const vsixPath = process.argv[2];
if (!vsixPath) {
  throw new Error('Usage: node ./scripts/assert-vsix-contents.js <path-to-vsix>');
}

const python = [
  'import json',
  'import sys',
  'import zipfile',
  'with zipfile.ZipFile(sys.argv[1]) as archive:',
  '    print(json.dumps(archive.namelist()))'
].join('\n');

const entries = JSON.parse(execFileSync('python3', ['-c', python, vsixPath], { encoding: 'utf8' }));
const result = validateVsixEntries(entries);

if (result.missing.length > 0 || result.forbidden.length > 0) {
  const message = [
    result.missing.length > 0 ? `Missing entries: ${result.missing.join(', ')}` : null,
    result.forbidden.length > 0 ? `Forbidden entries: ${result.forbidden.join(', ')}` : null
  ]
    .filter(Boolean)
    .join('\n');
  throw new Error(message);
}

process.stdout.write(`VSIX contents verified: ${vsixPath}\n`);
