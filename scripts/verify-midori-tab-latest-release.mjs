#!/usr/bin/env node

import fs from 'node:fs';

const metadataFile = process.argv[2];
if (!metadataFile) {
  throw new Error('Usage: verify-midori-tab-latest-release.mjs <upstream.json>');
}

const metadata = JSON.parse(fs.readFileSync(metadataFile, 'utf8'));
const repository = 'https://github.com/goastian/midori-tab';
const apiBase = 'https://api.github.com/repos/goastian/midori-tab';
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
const assetName = `midori-tab-${sourceVersion}-firefox.zip`;
const asset = (release.assets || []).find((candidate) => candidate.name === assetName);
const assetSha256 = String(asset?.digest || '').replace(/^sha256:/, '');

const expected = {
  sourceVersion,
  sourceRef: tag,
  releaseId: release.id,
  releaseUrl: `${repository}/releases/tag/${tag}`,
  publishedAt: release.published_at,
  immutable: release.immutable === true,
  tagArchiveUrl: `${apiBase}/tarball/${tag}`,
  assetId: asset?.id,
  assetName,
  assetUrl: `${repository}/releases/download/${tag}/${assetName}`,
  assetSha256,
};
const actual = {
  sourceVersion: metadata.sourceVersion,
  sourceRef: metadata.sourceRef,
  releaseId: metadata.release?.id,
  releaseUrl: metadata.release?.url,
  publishedAt: metadata.release?.publishedAt,
  immutable: metadata.release?.immutable === true,
  tagArchiveUrl: metadata.release?.tagArchiveUrl,
  assetId: metadata.release?.firefoxAsset?.id,
  assetName: metadata.release?.firefoxAsset?.name,
  assetUrl: metadata.release?.firefoxAsset?.url,
  assetSha256: metadata.release?.firefoxAsset?.sha256,
};
if (!asset || !/^[0-9a-f]{64}$/.test(assetSha256) || JSON.stringify(actual) !== JSON.stringify(expected)) {
  throw new Error('Candidate metadata is not the current stable Midori Tab release');
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

console.log(`Latest stable release verified again: ${tag} at ${metadata.sourceCommit}`);
