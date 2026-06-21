#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');

const { applyPublishVersion, derivePublishVersion } = require('./versioning');

const extensionRoot = path.resolve(__dirname, '..');
const packageJsonPath = path.join(extensionRoot, 'package.json');
const packageLockPath = path.join(extensionRoot, 'package-lock.json');

const runNumber = process.argv[2] || process.env.SOURCE_RUN_NUMBER || process.env.GITHUB_RUN_NUMBER;
if (!runNumber) {
  throw new Error('Missing run number. Pass it as the first argument or set SOURCE_RUN_NUMBER/GITHUB_RUN_NUMBER.');
}

const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
const packageLock = JSON.parse(fs.readFileSync(packageLockPath, 'utf8'));
const version = derivePublishVersion(packageJson.version, runNumber);

applyPublishVersion(packageJson, packageLock, version);

fs.writeFileSync(packageJsonPath, `${JSON.stringify(packageJson, null, 2)}\n`);
fs.writeFileSync(packageLockPath, `${JSON.stringify(packageLock, null, 2)}\n`);

process.stdout.write(`${version}\n`);
