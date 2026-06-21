function derivePublishVersion(baseVersion, runNumber) {
  if (!/^\d+\.\d+\.\d+$/.test(baseVersion)) {
    throw new Error(`Expected a semantic base version like 0.1.0, received: ${baseVersion}`);
  }

  const patch = Number.parseInt(String(runNumber), 10);
  if (!Number.isInteger(patch) || patch <= 0) {
    throw new Error(`Expected a positive run number, received: ${runNumber}`);
  }

  const [major, minor] = baseVersion.split('.');
  return `${major}.${minor}.${patch}`;
}

function applyPublishVersion(packageJson, packageLock, version) {
  packageJson.version = version;
  if (packageLock) {
    packageLock.version = version;
    if (packageLock.packages && packageLock.packages['']) {
      packageLock.packages[''].version = version;
    }
  }
  return {
    packageJson,
    packageLock
  };
}

module.exports = {
  applyPublishVersion,
  derivePublishVersion
};
