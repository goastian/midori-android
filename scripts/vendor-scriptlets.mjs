import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdtemp, mkdir, readFile, writeFile, copyFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve, join } from "node:path";
import { fileURLToPath, pathToFileURL, domainToASCII } from "node:url";

const root = resolve(fileURLToPath(new URL("..", import.meta.url)));
const destination = join(root, "app/src/main/assets/adblock/content/scriptlets");
const adguardVersion = "2.5.1";
const adguardIntegrity = "008B4qm6mXU4mqHOl1HE/jfRa0D/LUX2Lx4k00G45csiBF/fnIGzVivPsT1Q+Uhp9C+zj5tKoekkL39W8HqjzA==";
const ublockCommit = "c61688e27a53f9b07e07b34170a5e3e5029c35ac";
const suffixCommit = "52b71b3c4576ff77450cc7ebcfc7f34a916af3f9";
const temporary = await mkdtemp(join(tmpdir(), "midori-scriptlet-vendor-"));

async function download(url, target, integrity) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status}: ${url}`);
  const body = Buffer.from(await response.arrayBuffer());
  if (integrity && createHash("sha512").update(body).digest("base64") !== integrity) {
    throw new Error(`Integrity mismatch: ${url}`);
  }
  await writeFile(target, body);
  return createHash("sha256").update(body).digest("hex");
}

try {
  const adguardArchive = join(temporary, "adguard.tgz");
  const ublockArchive = join(temporary, "ublock.tgz");
  const adguardUrl = `https://registry.npmjs.org/@adguard/scriptlets/-/scriptlets-${adguardVersion}.tgz`;
  const ublockUrl = `https://codeload.github.com/gorhill/uBlock/tar.gz/${ublockCommit}`;
  const suffixUrl = `https://raw.githubusercontent.com/publicsuffix/list/${suffixCommit}/public_suffix_list.dat`;
  const suffixFile = join(temporary, "suffixes.txt");
  const [adguardHash, ublockHash, suffixHash] = await Promise.all([
    download(adguardUrl, adguardArchive, adguardIntegrity),
    download(ublockUrl, ublockArchive),
    download(suffixUrl, suffixFile),
  ]);
  execFileSync("tar", ["-xzf", adguardArchive, "-C", temporary]);
  execFileSync("tar", ["-xzf", ublockArchive, "-C", temporary]);
  const adguard = JSON.parse(await readFile(join(temporary, "package/dist/scriptlets.corelibs.json"), "utf8"));
  const ublockRoot = join(temporary, `uBlock-${ublockCommit}`);
  const { builtinScriptlets } = await import(pathToFileURL(join(ublockRoot, "src/js/resources/scriptlets.js")));
  const entries = {};
  for (const item of adguard.scriptlets) {
    const name = item.names[0];
    entries[`adg/${name}`] = {
      engine: "adg", name, aliases: item.names, world: "MAIN",
      trusted: name.startsWith("trusted-"), code: item.scriptlet, dependencies: [],
    };
  }
  for (const item of builtinScriptlets) {
    entries[`ubo/${item.name}`] = {
      engine: "ubo", name: item.name, aliases: [item.name, ...(item.aliases || [])],
      world: item.world || "MAIN", trusted: item.requiresTrust === true,
      code: item.fn.toString(), dependencies: (item.dependencies || []).map(name => `ubo/${name}`),
    };
  }
  for (const entry of Object.values(entries)) {
    for (const dependency of entry.dependencies) {
      if (!entries[dependency]) throw new Error(`Missing dependency: ${dependency}`);
    }
  }
  await mkdir(destination, { recursive: true });
  await writeFile(join(destination, "resources.json"), JSON.stringify({ adguardVersion, ublockCommit, entries }));
  const { default: redirects } = await import(pathToFileURL(join(ublockRoot, "src/js/redirect-resources.js")));
  const mimeTypes = { js: "application/javascript", json: "application/json", html: "text/html", css: "text/css", gif: "image/gif", png: "image/png", mp3: "audio/mp3", mp4: "video/mp4", xml: "text/xml" };
  const replacements = [];
  for (const [name, metadata] of redirects) {
    if (metadata.params) continue;
    const content = await readFile(join(ublockRoot, "src/web_accessible_resources", name));
    replacements.push({ name, aliases: metadata.alias ? [metadata.alias].flat() : [],
      kind: { mime: mimeTypes[name.split(".").at(-1)] || "text/plain" }, content: content.toString("base64") });
  }
  await writeFile(join(destination, "../../redirect-resources.json"), JSON.stringify(replacements));
  await copyFile(join(temporary, "package/LICENSE"), join(destination, "LICENSE-AdGuard.txt"));
  await copyFile(join(ublockRoot, "LICENSE.txt"), join(destination, "LICENSE-uBlock.txt"));
  const suffixes = (await readFile(suffixFile, "utf8")).split(/\r?\n/).map(line => line.trim())
    .filter(line => line && !line.startsWith("//")).map(line => {
      const prefix = line.startsWith("!") ? "!" : line.startsWith("*.") ? "*." : "";
      return prefix + domainToASCII(line.slice(prefix.length));
    });
  await writeFile(join(destination, "public-suffixes.json"), JSON.stringify(suffixes));
  await copyFile(join(root, "LICENSE"), join(destination, "LICENSE-PublicSuffixList.txt"));
  const metadata = {
    adguard: { version: adguardVersion, url: adguardUrl, sha256: adguardHash, integrity: `sha512-${adguardIntegrity}` },
    ublock: { commit: ublockCommit, url: ublockUrl, sha256: ublockHash },
    publicSuffixList: { commit: suffixCommit, url: suffixUrl, sha256: suffixHash, license: "MPL-2.0" },
  };
  await writeFile(join(destination, "sources.json"), JSON.stringify(metadata, null, 2) + "\n");
  const rows = Object.entries(entries).filter(([, entry]) => entry.engine !== "ubo" || entry.name.endsWith(".js"))
    .map(([id, entry]) => `${id}|${entry.trusted}|${entry.aliases.join(",")}`);
  const kotlin = `package org.midorinext.android.adblock

// Generated by scripts/vendor-scriptlets.mjs from the pinned upstream libraries.
internal object ScriptletCatalog {
    data class Entry(val id: String, val trusted: Boolean)

    private val entries = """
${rows.join("\n")}
""".trim().lineSequence().flatMap { row ->
        val parts = row.split('|')
        val entry = Entry(parts[0], parts[1].toBoolean())
        parts[2].split(',').asSequence().map { alias ->
            (parts[0].substringBefore('/') + "/" + alias) to entry
        }
    }.toMap()

    private val ids by lazy { entries.values.mapTo(hashSetOf()) { it.id } }

    fun containsId(id: String): Boolean = id in ids

    fun resolve(name: String, ublock: Boolean): Entry? {
        if (!ublock) return entries["adg/" + name]
        return entries["ubo/" + name.removeSuffix(".js") + ".js"]
    }
}
`;
  await writeFile(join(root, "app/src/main/java/org/midorinext/android/adblock/ScriptletCatalog.kt"), kotlin);
  console.log(`Vendored ${rows.length} scriptlets and ${Object.keys(entries).length - rows.length} dependencies`);
} finally {
  await rm(temporary, { recursive: true, force: true });
}
