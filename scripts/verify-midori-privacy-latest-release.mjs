#!/usr/bin/env node

import fs from 'node:fs';

const metadataFile = process.argv[2];
if (!metadataFile) {
  throw new Error('Usage: verify-midori-privacy-latest-release.mjs <upstream.json>');
}

const metadata = JSON.parse(fs.readFileSync(metadataFile, 'utf8'));
const repository = 'https://github.com/goastian/midori-privacy';
const apiBase = 'https://api.github.com/repos/goastian/midori-privacy';
const headers = {
  Accept: 'application/vnd.github+json',
  'X-GitHub-Api-Version': '2022-11-28',
  'User-Agent': 'midori-android-release-verifier',
};
if (process.env.GITHUB_TOKEN) {
  headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
}

async function fetchJson(url) {
  const response = await fetch(url, { headers, signal: AbortSignal.timeout(120_000) });
  if (!response.ok) {
    throw new Error(`GitHub API ${response.status} for ${url}`);
  }
  return response.json();
}

const release = await fetchJson(`${apiBase}/releases/latest`);
if (release.draft || release.prerelease) {
  throw new Error('GitHub latest release is not stable');
}

const tag = String(release.tag_name || '');
if (!/^v\d+\.\d+\.\d+$/.test(tag)) {
  throw new Error(`Unexpected latest release tag: ${tag}`);
}
const sourceVersion = tag.slice(1);
const assetName = `midori-privacy-${sourceVersion}-firefox.zip`;
const asset = (release.assets || []).find(candidate => candidate.name === assetName);
const assetDigest = String(asset?.digest || '');
const assetSha256 = assetDigest.replace(/^sha256:/, '');

const expected = {
  sourceRepository: repository,
  sourceVersion,
  sourceRef: tag,
  version: `${sourceVersion}.4`,
  compatibilityRevision: 4,
  releaseId: release.id,
  releaseUrl: `${repository}/releases/tag/${tag}`,
  publishedAt: release.published_at,
  immutable: release.immutable === true,
  tagArchiveUrl: `${apiBase}/tarball/${tag}`,
  assetId: asset?.id,
  assetName,
  assetUrl: `${repository}/releases/download/${tag}/${assetName}`,
  assetDigest,
  assetSha256,
};
const actual = {
  sourceRepository: metadata.sourceRepository,
  sourceVersion: metadata.sourceVersion,
  sourceRef: metadata.sourceRef,
  version: metadata.version,
  compatibilityRevision: metadata.compatibilityRevision,
  releaseId: metadata.release?.id,
  releaseUrl: metadata.release?.url,
  publishedAt: metadata.release?.publishedAt,
  immutable: metadata.release?.immutable === true,
  tagArchiveUrl: metadata.release?.tagArchiveUrl,
  assetId: metadata.release?.firefoxAsset?.id,
  assetName: metadata.release?.firefoxAsset?.name,
  assetUrl: metadata.release?.firefoxAsset?.url,
  assetDigest: metadata.release?.firefoxAsset?.digest,
  assetSha256: metadata.release?.firefoxAsset?.sha256,
};
if (!asset || !/^sha256:[0-9a-f]{64}$/.test(assetDigest) ||
    JSON.stringify(actual) !== JSON.stringify(expected)) {
  throw new Error('Candidate metadata is not the current stable Midori Privacy release');
}

const ref = await fetchJson(`${apiBase}/git/ref/tags/${encodeURIComponent(tag)}`);
let object = ref.object;
for (let depth = 0; object?.type === 'tag' && depth < 4; depth += 1) {
  const annotatedTag = await fetchJson(`${apiBase}/git/tags/${object.sha}`);
  object = annotatedTag.object;
}
if (object?.type !== 'commit' || object.sha !== metadata.sourceCommit) {
  throw new Error('Candidate commit is no longer the commit referenced by the latest release tag');
}
if (metadata.release?.sourceArchiveUrl !== `${apiBase}/tarball/${metadata.sourceCommit}`) {
  throw new Error('Candidate source archive is not pinned to the full release commit');
}

console.log(`Latest stable Midori Privacy release verified again: ${tag} at ${metadata.sourceCommit}`);
