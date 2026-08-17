#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const extensionRoot = path.resolve(process.argv[2] || '');
if (!extensionRoot || !fs.existsSync(extensionRoot) || !fs.statSync(extensionRoot).isDirectory()) {
  throw new Error('Expected the extracted MidoriVPN Firefox extension directory');
}

const ANDROID_COMPATIBILITY_REVISION = 1;
const MIDORI_VPN_EXTENSION_ID = 'midorivpn@astian.org';

function extensionPath(relativePath) {
  return path.join(extensionRoot, relativePath);
}

function replaceExact(relativePath, before, after) {
  const file = extensionPath(relativePath);
  const original = fs.readFileSync(file, 'utf8');
  const occurrences = original.split(before).length - 1;
  if (occurrences !== 1) {
    throw new Error(`${relativePath}: expected 1 match, found ${occurrences}`);
  }
  fs.writeFileSync(file, original.replace(before, after));
}

const manifestPath = extensionPath('manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const sourceVersion = String(manifest.version || '');
if (!/^\d+\.\d+\.\d+$/.test(sourceVersion) || manifest.manifest_version !== 3) {
  throw new Error(`Expected a three-part MV3 release, found ${sourceVersion}`);
}
if (manifest.action?.default_popup !== 'popup.html') {
  throw new Error('Expected MidoriVPN to expose popup.html through its action');
}
if (manifest.browser_specific_settings?.gecko?.id !== MIDORI_VPN_EXTENSION_ID) {
  throw new Error('Unexpected MidoriVPN WebExtension id');
}

manifest.version = `${sourceVersion}.${ANDROID_COMPATIBILITY_REVISION}`;
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);

replaceExact(
  'popup.html',
  '<title>ten - VPN</title>',
  '<title>MidoriVPN</title>',
);

console.log(`Patched MidoriVPN ${sourceVersion} for Android as ${manifest.version}`);
