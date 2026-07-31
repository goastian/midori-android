#!/usr/bin/env node

import fs from 'node:fs';

const [addon, currentFile, candidateFile] = process.argv.slice(2);
const profiles = {
  'midori-tab': {
    repository: 'https://github.com/goastian/midori-tab',
  },
  'midori-privacy': {
    repository: 'https://github.com/goastian/midori-privacy',
  },
};
const profile = profiles[addon];

if (!profile || !currentFile || !candidateFile) {
  throw new Error(
    'Usage: verify-midori-addon-update.mjs <midori-tab|midori-privacy> <current.json> <candidate.json>',
  );
}

const current = JSON.parse(fs.readFileSync(currentFile, 'utf8'));
const candidate = JSON.parse(fs.readFileSync(candidateFile, 'utf8'));

function parseVersion(value) {
  const text = String(value || '');
  if (!/^\d+(?:\.\d+){2,3}$/.test(text)) {
    throw new Error(`Unsupported ${addon} version: ${value}`);
  }
  return text.split('.').map(Number);
}

function compareVersions(left, right) {
  const leftParts = parseVersion(left);
  const rightParts = parseVersion(right);
  const length = Math.max(leftParts.length, rightParts.length);

  for (let index = 0; index < length; index += 1) {
    const difference = (leftParts[index] || 0) - (rightParts[index] || 0);
    if (difference !== 0) return Math.sign(difference);
  }
  return 0;
}

function requireSha256(value, label) {
  if (!/^[0-9a-f]{64}$/.test(String(value || ''))) {
    throw new Error(`Invalid ${addon} ${label}`);
  }
}

const expectedTag = `v${candidate.sourceVersion}`;
const expectedAssetName = `${addon}-${candidate.sourceVersion}-firefox.zip`;
const expectedAssetUrl =
  `${profile.repository}/releases/download/${expectedTag}/${expectedAssetName}`;

if (
  candidate.sourceRepository !== profile.repository ||
  candidate.release?.tag !== expectedTag ||
  candidate.release?.url !== `${profile.repository}/releases/tag/${expectedTag}` ||
  candidate.release?.firefoxAsset?.name !== expectedAssetName ||
  candidate.release?.firefoxAsset?.url !== expectedAssetUrl ||
  candidate.version !==
    `${candidate.sourceVersion}.${candidate.compatibilityRevision}` ||
  !Number.isInteger(candidate.compatibilityRevision) ||
  candidate.compatibilityRevision <= 0
) {
  throw new Error(`Invalid ${addon} release metadata`);
}

requireSha256(candidate.bundleSha256, 'bundle SHA-256');
requireSha256(candidate.release.firefoxAsset.sha256, 'Firefox asset SHA-256');

if (compareVersions(candidate.sourceVersion, current.sourceVersion) < 0) {
  throw new Error(
    `Refusing to move ${addon} backward from ${current.sourceVersion} to ${candidate.sourceVersion}`,
  );
}

if (
  candidate.bundleSha256 !== current.bundleSha256 &&
  compareVersions(candidate.version, current.version) <= 0
) {
  throw new Error(
    `Changed ${addon} bundle ${candidate.version} must be newer than ${current.version}`,
  );
}

console.log(`Update check passed: ${current.version} -> ${candidate.version}`);

