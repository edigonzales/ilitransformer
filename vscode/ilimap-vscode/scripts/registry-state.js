#!/usr/bin/env node

const https = require('node:https');

function fetch(url, options = {}) {
  return new Promise((resolve, reject) => {
    const request = https.request(url, options, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => {
        body += chunk;
      });
      response.on('end', () => {
        resolve({
          statusCode: response.statusCode || 0,
          body
        });
      });
    });
    request.on('error', reject);
    if (options.body) {
      request.write(options.body);
    }
    request.end();
  });
}

function marketplaceQuery(publisher, extensionName) {
  return JSON.stringify({
    filters: [
      {
        criteria: [
          {
            filterType: 10,
            value: `${publisher}.${extensionName}`
          }
        ],
        pageNumber: 1,
        pageSize: 1,
        sortBy: 0,
        sortOrder: 0
      }
    ],
    assetTypes: [],
    flags: 103
  });
}

async function isPublishedToMarketplace(publisher, extensionName, version) {
  const body = marketplaceQuery(publisher, extensionName);
  const response = await fetch(
    'https://marketplace.visualstudio.com/_apis/public/gallery/extensionquery?api-version=7.2-preview.1',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json;api-version=7.2-preview.1'
      },
      body
    }
  );

  if (response.statusCode !== 200) {
    throw new Error(`Marketplace query failed with status ${response.statusCode}`);
  }

  const payload = JSON.parse(response.body);
  const versions = payload.results?.[0]?.extensions?.[0]?.versions || [];
  return versions.some((candidate) => candidate.version === version);
}

async function isPublishedToOpenVsx(publisher, extensionName, version) {
  const response = await fetch(
    `https://open-vsx.org/api/${encodeURIComponent(publisher)}/${encodeURIComponent(extensionName)}/${encodeURIComponent(version)}`
  );

  if (response.statusCode === 404) {
    return false;
  }
  if (response.statusCode !== 200) {
    throw new Error(`Open VSX query failed with status ${response.statusCode}`);
  }
  return true;
}

async function main() {
  const [publisher, extensionName, version] = process.argv.slice(2);
  if (!publisher || !extensionName || !version) {
    throw new Error('Usage: node ./scripts/registry-state.js <publisher> <extensionName> <version>');
  }

  const [marketplace, openvsx] = await Promise.all([
    isPublishedToMarketplace(publisher, extensionName, version),
    isPublishedToOpenVsx(publisher, extensionName, version)
  ]);

  process.stdout.write(
    `${JSON.stringify({
      marketplace,
      openvsx
    })}\n`
  );
}

if (require.main === module) {
  main().catch((error) => {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  });
}

module.exports = {
  isPublishedToMarketplace,
  isPublishedToOpenVsx,
  marketplaceQuery
};
