#!/usr/bin/env node

import fs from 'node:fs';

const [currentFile, candidateFile] = process.argv.slice(2);
if (!currentFile || !candidateFile) {
  throw new Error('Usage: verify-midori-privacy-update.mjs <current-upstream.json> <candidate-upstream.json>');
}

const current = JSON.parse(fs.readFileSync(currentFile, 'utf8'));
const candidate = JSON.parse(fs.readFileSync(candidateFile, 'utf8'));

function parseVersion(value) {
  const text = String(value || '');
  if (!/^\d+(?:\.\d+){2,3}$/.test(text)) {
    throw new Error(`Unsupported Midori Privacy version: ${value}`);
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

function requireSha256(value, label) {
  if (!/^[0-9a-f]{64}$/.test(String(value || ''))) {
    throw new Error(`Invalid ${label}`);
  }
}

if (candidate.sourceRepository !== 'https://github.com/goastian/midori-privacy') {
  throw new Error('Candidate does not come from the canonical Midori Privacy repository');
}
if (!/^v\d+\.\d+\.\d+$/.test(String(candidate.sourceRef || '')) ||
    candidate.sourceRef !== candidate.release?.tag ||
    candidate.sourceRef.slice(1) !== candidate.sourceVersion) {
  throw new Error('Candidate release tag and source version are inconsistent');
}
if (!/^[0-9a-f]{40}$/.test(String(candidate.sourceCommit || ''))) {
  throw new Error('Candidate source commit is invalid');
}
if (candidate.compatibilityRevision !== 5 ||
    candidate.version !== `${candidate.sourceVersion}.5`) {
  throw new Error('Candidate does not use Android compatibility revision 5');
}
if (candidate.buildTarget !== 'firefox-android' ||
    candidate.importMode !== 'verified-official-firefox-release-asset' ||
    candidate.compatibilityPatch !== 'scripts/patch-midori-privacy-firefox-android.mjs') {
  throw new Error('Candidate import or compatibility patch contract is invalid');
}
requireSha256(candidate.bundleSha256, 'candidate bundle SHA-256');
requireSha256(candidate.compatibilityPatchSha256, 'compatibility patch SHA-256');
requireSha256(candidate.release?.sourceArchiveSha256, 'source archive SHA-256');
requireSha256(candidate.release?.firefoxAsset?.sha256, 'Firefox asset SHA-256');
if (candidate.release?.firefoxAsset?.digest !==
    `sha256:${candidate.release?.firefoxAsset?.sha256}`) {
  throw new Error('Candidate Firefox asset digest is inconsistent');
}

if (compareVersions(candidate.sourceVersion, current.sourceVersion) < 0) {
  throw new Error(
    `Refusing to move Midori Privacy release backward from ${current.sourceVersion} to ${candidate.sourceVersion}`,
  );
}

if (
  candidate.bundleSha256 !== current.bundleSha256 &&
  compareVersions(candidate.version, current.version) <= 0
) {
  throw new Error(
    `Changed Midori Privacy bundle ${candidate.version} must be newer than installed bundle ${current.version}`,
  );
}

console.log(
  `Trusted update check passed: ${current.version} -> ${candidate.version} (${candidate.release.tag})`,
);
