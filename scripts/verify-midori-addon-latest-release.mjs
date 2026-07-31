#!/usr/bin/env node

import fs from 'node:fs';

const [addon, metadataFile] = process.argv.slice(2);
const profiles = {
  'midori-tab': {
    repository: 'https://github.com/goastian/midori-tab',
  },
  'midori-privacy': {
    repository: 'https://github.com/goastian/midori-privacy',
  },
};
const profile = profiles[addon];

if (!profile || !metadataFile) {
  throw new Error(
    'Usage: verify-midori-addon-latest-release.mjs <midori-tab|midori-privacy> <upstream.json>',
  );
}

const metadata = JSON.parse(fs.readFileSync(metadataFile, 'utf8'));
const apiUrl = profile.repository.replace('https://github.com/', 'https://api.github.com/repos/');
const headers = {
  Accept: 'application/vnd.github+json',
  'X-GitHub-Api-Version': '2022-11-28',
  'User-Agent': 'midori-android-release-verifier',
};
if (process.env.GITHUB_TOKEN) {
  headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
}

const response = await fetch(`${apiUrl}/releases/latest`, {
  headers,
  signal: AbortSignal.timeout(120_000),
});
if (!response.ok) {
  throw new Error(`GitHub API ${response.status} for ${addon}`);
}

const release = await response.json();
const tag = String(release.tag_name || '');
const sourceVersion = tag.replace(/^v/, '');
const assetName = `${addon}-${sourceVersion}-firefox.zip`;
const asset = release.assets?.find((candidate) => candidate.name === assetName);
const assetSha256 = String(asset?.digest || '').replace(/^sha256:/, '');

const expected = {
  sourceRepository: profile.repository,
  sourceVersion,
  tag,
  releaseUrl: `${profile.repository}/releases/tag/${tag}`,
  assetName,
  assetUrl: `${profile.repository}/releases/download/${tag}/${assetName}`,
  assetSha256,
};
const actual = {
  sourceRepository: metadata.sourceRepository,
  sourceVersion: metadata.sourceVersion,
  tag: metadata.release?.tag,
  releaseUrl: metadata.release?.url,
  assetName: metadata.release?.firefoxAsset?.name,
  assetUrl: metadata.release?.firefoxAsset?.url,
  assetSha256: metadata.release?.firefoxAsset?.sha256,
};

if (
  release.draft ||
  release.prerelease ||
  !/^v\d+\.\d+\.\d+$/.test(tag) ||
  !asset ||
  !/^[0-9a-f]{64}$/.test(assetSha256) ||
  JSON.stringify(actual) !== JSON.stringify(expected)
) {
  throw new Error(`${addon} metadata is not the latest stable release`);
}

console.log(`Latest stable release verified: ${addon} ${tag}`);

