#!/usr/bin/env node

import fs from 'node:fs';

const [currentFile, candidateFile] = process.argv.slice(2);
if (!currentFile || !candidateFile) {
  throw new Error('Usage: verify-midori-tab-update.mjs <current-upstream.json> <candidate-upstream.json>');
}

const current = JSON.parse(fs.readFileSync(currentFile, 'utf8'));
const candidate = JSON.parse(fs.readFileSync(candidateFile, 'utf8'));

function parseVersion(value) {
  const text = String(value || '');
  if (!/^\d+(?:\.\d+){2,3}$/.test(text)) {
    throw new Error(`Unsupported Midori Tab version: ${value}`);
  }
  return text.split('.').map(Number);
}

function compareVersions(left, right) {
  const a = parseVersion(left);
  const b = parseVersion(right);
  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index += 1) {
    const difference = (a[index] || 0) - (b[index] || 0);
    if (difference !== 0) return Math.sign(difference);
  }
  return 0;
}

if (compareVersions(candidate.sourceVersion, current.sourceVersion) < 0) {
  throw new Error(
    `Refusing to move Midori Tab release backward from ${current.sourceVersion} to ${candidate.sourceVersion}`,
  );
}

if (
  candidate.bundleSha256 !== current.bundleSha256 &&
  compareVersions(candidate.version, current.version) <= 0
) {
  throw new Error(
    `Changed Midori Tab bundle ${candidate.version} must be newer than installed bundle ${current.version}`,
  );
}

console.log(
  `Trusted update check passed: ${current.version} -> ${candidate.version} (${candidate.release?.tag || 'unknown release'})`,
);
