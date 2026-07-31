#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_dir="$repo_root/app/src/main/assets/extensions/midori_privacy"
canonical_source_repository="https://github.com/goastian/midori-privacy"
api_base="https://api.github.com/repos/goastian/midori-privacy"
release_api_url="$api_base/releases/latest"
work_dir="$(mktemp -d)"
staging_dir="$work_dir/extension"
release_json="$work_dir/release.json"
post_release_json="$work_dir/release-after-import.json"
tag_ref_json="$work_dir/tag-ref.json"
post_tag_ref_json="$work_dir/tag-ref-after-import.json"
source_archive="$work_dir/source.tar.gz"
source_entries="$work_dir/source-entries.txt"
source_package_json="$work_dir/source-package.json"
firefox_archive="$work_dir/release-firefox.zip"
firefox_entries="$work_dir/firefox-entries.txt"
current_metadata_snapshot="$work_dir/current-upstream.json"
replacement_dir="${target_dir}.next"
backup_dir="${target_dir}.previous"
replace_started=false

cleanup() {
    rm -rf "$work_dir"
    rm -rf "$replacement_dir"
    if [[ "$replace_started" == true && ! -e "$target_dir" && -e "$backup_dir" ]]; then
        mv "$backup_dir" "$target_dir"
    fi
}
trap cleanup EXIT

umask 077
mkdir -p "$staging_dir"

if [[ "$#" -ne 0 ]]; then
    echo "Midori Privacy refs are not accepted; the updater always uses the latest stable GitHub release" >&2
    exit 1
fi

for deprecated_override in MIDORI_PRIVACY_REF MIDORI_PRIVACY_REPOSITORY MIDORI_PRIVACY_SOURCE; do
    if [[ -n "${!deprecated_override:-}" ]]; then
        echo "$deprecated_override is not supported; the updater always downloads the latest stable release" >&2
        exit 1
    fi
done

# The updater imports the official release ZIP rather than executing upstream
# build code. Keep all GitHub downloads credential-free, then pass only the
# verified regular files to the trusted PR job.
unset GITHUB_TOKEN

trusted_patch_sha256="$(sha256sum "$repo_root/scripts/patch-midori-privacy-firefox-android.mjs" | awk '{print $1}')"
if [[ -f "$target_dir/upstream.json" ]]; then
    cp "$target_dir/upstream.json" "$current_metadata_snapshot"
fi

github_curl=(
    curl --fail --location --silent --show-error
    --retry 3 --retry-delay 2 --connect-timeout 15 --max-time 120
    -H "Accept: application/vnd.github+json"
    -H "X-GitHub-Api-Version: 2022-11-28"
)

"${github_curl[@]}" -o "$release_json" "$release_api_url"
readarray -t release_values < <(
    node -e '
        const fs = require("fs");
        const [file, repository, apiBase] = process.argv.slice(1);
        const release = JSON.parse(fs.readFileSync(file, "utf8"));
        if (release.draft || release.prerelease) {
            throw new Error("GitHub latest release must be stable and published");
        }
        const tag = String(release.tag_name || "");
        if (!/^v\d+\.\d+\.\d+$/.test(tag)) {
            throw new Error(`Unexpected stable release tag: ${tag}`);
        }
        const version = tag.slice(1);
        const assetName = `midori-privacy-${version}-firefox.zip`;
        const asset = (release.assets || []).find(candidate => candidate.name === assetName);
        const digest = String(asset?.digest || "");
        const sha256 = digest.replace(/^sha256:/, "");
        if (release.html_url !== `${repository}/releases/tag/${tag}` ||
            release.tarball_url !== `${apiBase}/tarball/${tag}`) {
            throw new Error("Latest release does not point to the canonical Midori Privacy repository");
        }
        if (!asset || !Number.isSafeInteger(asset.id)) {
            throw new Error(`Latest release is missing ${assetName}`);
        }
        if (asset.browser_download_url !== `${repository}/releases/download/${tag}/${assetName}`) {
            throw new Error("Firefox release asset URL is not canonical");
        }
        if (!/^sha256:[0-9a-f]{64}$/.test(digest)) {
            throw new Error("Firefox release asset is missing its GitHub SHA-256 digest");
        }
        if (!Number.isSafeInteger(release.id) || !/^\d{4}-\d{2}-\d{2}T/.test(release.published_at || "")) {
            throw new Error("Latest release metadata is incomplete");
        }
        console.log(tag);
        console.log(version);
        console.log(release.id);
        console.log(release.html_url);
        console.log(release.published_at);
        console.log(release.tarball_url);
        console.log(asset.id);
        console.log(asset.name);
        console.log(asset.browser_download_url);
        console.log(digest);
        console.log(sha256);
        console.log(release.immutable === true ? "true" : "false");
    ' "$release_json" "$canonical_source_repository" "$api_base"
)

release_tag="${release_values[0]}"
release_version="${release_values[1]}"
release_id="${release_values[2]}"
release_url="${release_values[3]}"
release_published_at="${release_values[4]}"
release_tag_archive_url="${release_values[5]}"
release_asset_id="${release_values[6]}"
release_asset_name="${release_values[7]}"
release_asset_url="${release_values[8]}"
release_asset_digest="${release_values[9]}"
release_asset_sha256="${release_values[10]}"
release_immutable="${release_values[11]}"

tag_ref_url="$api_base/git/ref/tags/${release_tag}"
"${github_curl[@]}" -o "$tag_ref_json" "$tag_ref_url"
readarray -t tag_ref_values < <(
    node -e '
        const ref = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
        console.log(ref.object?.type ?? "");
        console.log(ref.object?.sha ?? "");
    ' "$tag_ref_json"
)
tag_ref_object_type="${tag_ref_values[0]}"
tag_ref_object_sha="${tag_ref_values[1]}"
tag_object_type="$tag_ref_object_type"
tag_object_sha="$tag_ref_object_sha"
tag_depth=0
while [[ "$tag_object_type" == "tag" && "$tag_depth" -lt 4 ]]; do
    tag_object_json="$work_dir/tag-object-${tag_depth}.json"
    "${github_curl[@]}" -o "$tag_object_json" "$api_base/git/tags/${tag_object_sha}"
    readarray -t tag_object_values < <(
        node -e '
            const tag = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
            console.log(tag.object?.type ?? "");
            console.log(tag.object?.sha ?? "");
        ' "$tag_object_json"
    )
    tag_object_type="${tag_object_values[0]}"
    tag_object_sha="${tag_object_values[1]}"
    tag_depth=$((tag_depth + 1))
done
if [[ "$tag_object_type" != "commit" || ! "$tag_object_sha" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Release tag does not resolve to a canonical Git commit" >&2
    exit 1
fi
source_commit="$tag_object_sha"

source_archive_url="$api_base/tarball/${source_commit}"
"${github_curl[@]}" -o "$source_archive" "$source_archive_url"
source_archive_sha256="$(sha256sum "$source_archive" | awk '{print $1}')"
tar -tzf "$source_archive" > "$source_entries"
archive_root="$(sed -n '1p' "$source_entries" | cut -d/ -f1)"
if [[ -z "$archive_root" || "$archive_root" != *-"${source_commit:0:7}" ]]; then
    echo "Release source archive does not match tag commit $source_commit" >&2
    exit 1
fi
node -e '
    const fs = require("fs");
    const [entriesFile, expectedRoot] = process.argv.slice(1);
    const entries = fs.readFileSync(entriesFile, "utf8").split("\n").filter(Boolean);
    if (entries.length === 0 || entries.length > 50000) {
        throw new Error(`Unsafe source archive entry count: ${entries.length}`);
    }
    const seen = new Set();
    for (const raw of entries) {
        if (raw.includes("\\") || raw.includes("\r") || raw.startsWith("/") || /^[A-Za-z]:/.test(raw)) {
            throw new Error(`Unsafe source archive path: ${JSON.stringify(raw)}`);
        }
        const path = raw.endsWith("/") ? raw.slice(0, -1) : raw;
        const parts = path.split("/");
        if (parts[0] !== expectedRoot || parts.some(part => !part || part === "." || part === "..")) {
            throw new Error(`Unsafe source archive path: ${JSON.stringify(raw)}`);
        }
        if (seen.has(path)) {
            throw new Error(`Duplicate source archive path: ${JSON.stringify(raw)}`);
        }
        seen.add(path);
    }
' "$source_entries" "$archive_root"

tar -xOf "$source_archive" "$archive_root/package.json" > "$source_package_json"
source_version="$(node -e '
    const manifest = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
    process.stdout.write(String(manifest.version || ""));
' "$source_package_json")"
source_dist_version="$(tar -xOf "$source_archive" "$archive_root/dist/version")"
if [[ "$source_version" != "$release_version" || "$source_dist_version" != "$release_version" ]]; then
    echo "Release tag $release_tag does not match source versions ($source_version, $source_dist_version)" >&2
    exit 1
fi

"${github_curl[@]}" -o "$firefox_archive" "$release_asset_url"
actual_release_asset_sha256="$(sha256sum "$firefox_archive" | awk '{print $1}')"
if [[ "$actual_release_asset_sha256" != "$release_asset_sha256" ]]; then
    echo "Firefox release asset SHA-256 does not match GitHub metadata" >&2
    exit 1
fi

readarray -t zip_limits < <(
    zipinfo -t "$firefox_archive" | node -e '
        let text = "";
        process.stdin.setEncoding("utf8");
        process.stdin.on("data", chunk => { text += chunk; });
        process.stdin.on("end", () => {
            const match = text.match(/(\d+) files?, (\d+) bytes uncompressed/);
            if (!match) throw new Error("Unable to read Firefox ZIP limits");
            const files = Number(match[1]);
            const bytes = Number(match[2]);
            if (!Number.isSafeInteger(files) || files < 1 || files > 10000) {
                throw new Error(`Unsafe Firefox ZIP entry count: ${files}`);
            }
            if (!Number.isSafeInteger(bytes) || bytes < 1 || bytes > 256 * 1024 * 1024) {
                throw new Error(`Unsafe Firefox ZIP uncompressed size: ${bytes}`);
            }
            console.log(files);
            console.log(bytes);
        });
    '
)
zipinfo -1 "$firefox_archive" > "$firefox_entries"
node -e '
    const fs = require("fs");
    const [entriesFile, expectedCount] = process.argv.slice(1);
    const text = fs.readFileSync(entriesFile, "utf8");
    const entries = text.endsWith("\n") ? text.slice(0, -1).split("\n") : text.split("\n");
    if (entries.length !== Number(expectedCount)) {
        throw new Error(`Ambiguous Firefox ZIP paths: expected ${expectedCount}, listed ${entries.length}`);
    }
    const seen = new Set();
    for (const raw of entries) {
        if (!raw || raw.includes("\\") || raw.includes("\r") || raw.startsWith("/") || /^[A-Za-z]:/.test(raw)) {
            throw new Error(`Unsafe Firefox ZIP path: ${JSON.stringify(raw)}`);
        }
        const path = raw.endsWith("/") ? raw.slice(0, -1) : raw;
        const parts = path.split("/");
        if (parts.some(part => !part || part === "." || part === "..")) {
            throw new Error(`Unsafe Firefox ZIP path: ${JSON.stringify(raw)}`);
        }
        if (seen.has(path)) {
            throw new Error(`Duplicate Firefox ZIP path: ${JSON.stringify(raw)}`);
        }
        seen.add(path);
    }
    if (!seen.has("manifest.json") || !seen.has("background.html") ||
        !seen.has("js/start.js") || !seen.has("js/midori-stats.js") || !seen.has("LICENSE.txt")) {
        throw new Error("Firefox ZIP is missing required Midori Privacy files");
    }
' "$firefox_entries" "${zip_limits[0]}"
special_zip_entry="$(zipinfo -l "$firefox_archive" | awk '$1 ~ /^[lbcps]/ { print; exit }')"
if [[ -n "$special_zip_entry" ]]; then
    echo "Firefox ZIP contains a special filesystem entry: $special_zip_entry" >&2
    exit 1
fi

unzip -qq "$firefox_archive" -d "$staging_dir"
unexpected_entry="$(find "$staging_dir" -mindepth 1 ! -type f ! -type d -print -quit)"
if [[ -n "$unexpected_entry" ]]; then
    echo "Firefox ZIP extracted a non-regular entry: $unexpected_entry" >&2
    exit 1
fi

readarray -t official_manifest_values < <(
    node -e '
        const manifest = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
        console.log(manifest.manifest_version ?? "");
        console.log(manifest.version ?? "");
        console.log(manifest.browser_specific_settings?.gecko?.id ?? "");
        console.log(manifest.background?.page ?? "");
    ' "$staging_dir/manifest.json"
)
if [[ "${official_manifest_values[0]}" != "2" ||
      "${official_manifest_values[1]}" != "$release_version" ||
      "${official_manifest_values[2]}" != "midori-protection@astian.org" ||
      "${official_manifest_values[3]}" != "background.html" ]]; then
    echo "Official Firefox asset does not match the release or extension contract" >&2
    exit 1
fi

current_patch_sha256="$(sha256sum "$repo_root/scripts/patch-midori-privacy-firefox-android.mjs" | awk '{print $1}')"
if [[ "$current_patch_sha256" != "$trusted_patch_sha256" ]]; then
    echo "The trusted Android compatibility patch changed while downloading the release" >&2
    exit 1
fi
node "$repo_root/scripts/patch-midori-privacy-firefox-android.mjs" "$staging_dir"
node --check "$staging_dir/js/midori-stats.js"
cp "$staging_dir/LICENSE.txt" "$staging_dir/LICENSE.upstream"

# Close the release/tag TOCTOU window after importing and patching the asset.
"${github_curl[@]}" -o "$post_release_json" "$release_api_url"
"${github_curl[@]}" -o "$post_tag_ref_json" "$tag_ref_url"
node -e '
    const fs = require("fs");
    const [beforeFile, afterFile, refFile, expectedTag, expectedRefType, expectedRefSha] = process.argv.slice(1);
    const before = JSON.parse(fs.readFileSync(beforeFile, "utf8"));
    const after = JSON.parse(fs.readFileSync(afterFile, "utf8"));
    const ref = JSON.parse(fs.readFileSync(refFile, "utf8"));
    const snapshot = release => {
        const version = String(release.tag_name || "").replace(/^v/, "");
        const assetName = `midori-privacy-${version}-firefox.zip`;
        const asset = (release.assets || []).find(candidate => candidate.name === assetName);
        return {
            id: release.id,
            tag: release.tag_name,
            url: release.html_url,
            publishedAt: release.published_at,
            tarballUrl: release.tarball_url,
            immutable: release.immutable === true,
            draft: release.draft === true,
            prerelease: release.prerelease === true,
            asset: asset && {
                id: asset.id,
                name: asset.name,
                url: asset.browser_download_url,
                digest: asset.digest,
            },
        };
    };
    if (JSON.stringify(snapshot(before)) !== JSON.stringify(snapshot(after))) {
        throw new Error("Latest Midori Privacy release changed while it was being synchronized");
    }
    if (ref.ref !== `refs/tags/${expectedTag}` ||
        ref.object?.type !== expectedRefType || ref.object?.sha !== expectedRefSha) {
        throw new Error("Latest Midori Privacy release tag moved while it was being synchronized");
    }
' "$release_json" "$post_release_json" "$post_tag_ref_json" "$release_tag" "$tag_ref_object_type" "$tag_ref_object_sha"

expected_permissions='["alarms","dns","menus","privacy","storage","tabs","unlimitedStorage","webNavigation","webRequest","webRequestBlocking","<all_urls>"]'
patched_manifest_output="$(
    node -e '
        const fs = require("fs");
        const [manifestFile, statsFile, expectedPermissions] = process.argv.slice(1);
        const manifest = JSON.parse(fs.readFileSync(manifestFile, "utf8"));
        const stats = fs.readFileSync(statsFile, "utf8");
        if (JSON.stringify(manifest.permissions || []) !== expectedPermissions) {
            throw new Error("Midori Privacy permissions changed; review the upstream release before updating");
        }
        const markers = [
            "const STATS_ACTION =",
            "get-stats-summary",
            "const ALLOWED_SENDER_IDS = new Set([",
            "midoritabs@astian.org",
            "const ESTIMATED_BYTES_PER_BLOCK = 8 * 1024;",
            "ALLOWED_SENDER_IDS.has(sender.id) === false",
            "Math.min(\n        Number.MAX_SAFE_INTEGER,\n        blocked * ESTIMATED_BYTES_PER_BLOCK",
            "estimatedDataSavedBytes,",
            "dataSavedEstimateModel:",
            "conservative-8kib-per-block-v1",
        ];
        const missingMarkers = markers.filter(marker => !stats.includes(marker));
        if (missingMarkers.length > 0) {
            throw new Error(
                `Patched Midori Privacy statistics bridge is incomplete: ${missingMarkers.join(", ")}`
            );
        }
        console.log(manifest.manifest_version ?? "");
        console.log(manifest.browser_specific_settings?.gecko?.id ?? "");
        console.log(manifest.background?.page ?? "");
        console.log(manifest.version ?? "");
    ' "$staging_dir/manifest.json" "$staging_dir/js/midori-stats.js" "$expected_permissions"
)"
readarray -t patched_manifest_values <<< "$patched_manifest_output"
if [[ "${patched_manifest_values[0]}" != "2" ||
      "${patched_manifest_values[1]}" != "midori-protection@astian.org" ||
      "${patched_manifest_values[2]}" != "background.html" ||
      "${patched_manifest_values[3]}" != "$release_version.5" ]]; then
    echo "Unexpected patched Midori Privacy Firefox manifest contract" >&2
    exit 1
fi
android_version="${patched_manifest_values[3]}"
compatibility_revision=5

bundle_sha256="$({
    cd "$staging_dir"
    LC_ALL=C find . -type f ! -name upstream.json -print0 | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | awk '{print $1}'
})"
compatibility_patch_sha256="$(sha256sum "$repo_root/scripts/patch-midori-privacy-firefox-android.mjs" | awk '{print $1}')"
if [[ "$compatibility_patch_sha256" != "$trusted_patch_sha256" ]]; then
    echo "The trusted Android compatibility patch changed before bundle publication" >&2
    exit 1
fi

node -e '
    const fs = require("fs");
    const [file, repository, sourceRef, sourceCommit, sourceVersion, version,
        compatibilityRevision, bundleSha256, compatibilityPatchSha256, releaseId,
        releaseUrl, releasePublishedAt, releaseImmutable, releaseTagArchiveUrl,
        sourceArchiveUrl, sourceArchiveSha256, assetId, assetName, assetUrl,
        assetDigest, assetSha256] = process.argv.slice(1);
    fs.writeFileSync(file, `${JSON.stringify({
        sourceRepository: repository,
        sourceRef,
        sourceCommit,
        sourceVersion,
        version,
        compatibilityRevision: Number(compatibilityRevision),
        bundleSha256,
        buildTarget: "firefox-android",
        importMode: "verified-official-firefox-release-asset",
        compatibilityPatch: "scripts/patch-midori-privacy-firefox-android.mjs",
        compatibilityPatchSha256,
        release: {
            id: Number(releaseId),
            tag: sourceRef,
            url: releaseUrl,
            publishedAt: releasePublishedAt,
            immutable: releaseImmutable === "true",
            tagArchiveUrl: releaseTagArchiveUrl,
            sourceArchiveUrl,
            sourceArchiveSha256,
            firefoxAsset: {
                id: Number(assetId),
                name: assetName,
                url: assetUrl,
                digest: assetDigest,
                sha256: assetSha256,
            },
        },
    }, null, 2)}\n`);
' "$staging_dir/upstream.json" "$canonical_source_repository" "$release_tag" "$source_commit" "$source_version" \
    "$android_version" "$compatibility_revision" "$bundle_sha256" "$compatibility_patch_sha256" "$release_id" \
    "$release_url" "$release_published_at" "$release_immutable" "$release_tag_archive_url" "$source_archive_url" \
    "$source_archive_sha256" "$release_asset_id" "$release_asset_name" "$release_asset_url" \
    "$release_asset_digest" "$release_asset_sha256"

if [[ -f "$current_metadata_snapshot" ]]; then
    node "$repo_root/scripts/verify-midori-privacy-update.mjs" \
        "$current_metadata_snapshot" "$staging_dir/upstream.json"
fi

case "$target_dir" in
    "$repo_root/app/src/main/assets/extensions/midori_privacy") ;;
    *)
        echo "Refusing to replace unexpected target: $target_dir" >&2
        exit 1
        ;;
esac

rm -rf "$replacement_dir"
rm -rf "$backup_dir"
mkdir -p "$(dirname "$target_dir")"
mv "$staging_dir" "$replacement_dir"
replace_started=true
if [[ -e "$target_dir" ]]; then
    mv "$target_dir" "$backup_dir"
fi
mv "$replacement_dir" "$target_dir"
replace_started=false
rm -rf "$backup_dir"

echo "Synced Midori Privacy release $release_tag as Android bundle $android_version at $source_commit"
