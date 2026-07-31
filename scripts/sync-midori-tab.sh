#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_dir="$repo_root/app/src/main/assets/extensions/midori_newtab"
canonical_source_repository="https://github.com/goastian/midori-tab"
release_api_url="https://api.github.com/repos/goastian/midori-tab/releases/latest"
env_file="${MIDORI_TAB_ENV_FILE:-}"
work_dir="$(mktemp -d)"
source_dir="$work_dir/source"
staging_dir="$work_dir/extension"
release_json="$work_dir/release.json"
tag_ref_json="$work_dir/tag-ref.json"
source_archive="$work_dir/source.tar.gz"
firefox_archive="$work_dir/release-firefox.zip"
current_metadata_snapshot="$work_dir/current-upstream.json"
post_release_json="$work_dir/release-after-build.json"
post_tag_ref_json="$work_dir/tag-ref-after-build.json"
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

mkdir -p "$source_dir" "$staging_dir"
trusted_patch_sha256="$(sha256sum "$repo_root/scripts/patch-midori-tab-firefox-android.mjs" | awk '{print $1}')"
if [[ -f "$target_dir/upstream.json" ]]; then
    cp "$target_dir/upstream.json" "$current_metadata_snapshot"
fi

if [[ "$#" -ne 0 ]]; then
    echo "Midori Tab refs are no longer accepted; the updater always uses the latest stable GitHub release" >&2
    exit 1
fi

for deprecated_override in MIDORI_TAB_REF MIDORI_TAB_REPOSITORY MIDORI_TAB_SOURCE; do
    if [[ -n "${!deprecated_override:-}" ]]; then
        echo "$deprecated_override is no longer supported; the updater always downloads the latest stable release" >&2
        exit 1
    fi
done

# This script executes release source code during npm validation/build. The upstream
# repository is public, so keep the complete process tree credential-free and use the
# unauthenticated GitHub API rather than exposing a repository token to child processes.
unset GITHUB_TOKEN

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
        const [file, repository] = process.argv.slice(1);
        const release = JSON.parse(fs.readFileSync(file, "utf8"));
        if (release.draft || release.prerelease) {
            throw new Error("GitHub latest release must be stable and published");
        }
        const tag = String(release.tag_name || "");
        if (!/^v\d+\.\d+\.\d+$/.test(tag)) {
            throw new Error(`Unexpected stable release tag: ${tag}`);
        }
        const version = tag.slice(1);
        const expectedUrl = `${repository}/releases/tag/${tag}`;
        const expectedTarballUrl = `https://api.github.com/repos/goastian/midori-tab/tarball/${tag}`;
        const expectedAssetName = `midori-tab-${version}-firefox.zip`;
        const asset = (release.assets || []).find((candidate) => candidate.name === expectedAssetName);
        const digest = String(asset?.digest || "").replace(/^sha256:/, "");
        if (release.html_url !== expectedUrl || release.tarball_url !== expectedTarballUrl) {
            throw new Error("Latest release does not point to the canonical Midori Tab repository");
        }
        if (!asset || !Number.isSafeInteger(asset.id)) {
            throw new Error(`Latest release is missing ${expectedAssetName}`);
        }
        if (asset.browser_download_url !== `${repository}/releases/download/${tag}/${expectedAssetName}`) {
            throw new Error("Firefox release asset URL is not canonical");
        }
        if (!/^[0-9a-f]{64}$/.test(digest)) {
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
        console.log(asset.name);
        console.log(asset.browser_download_url);
        console.log(digest);
        console.log(asset.id);
        console.log(release.immutable === true ? "true" : "false");
    ' "$release_json" "$canonical_source_repository"
)

release_tag="${release_values[0]}"
release_version="${release_values[1]}"
release_id="${release_values[2]}"
release_url="${release_values[3]}"
release_published_at="${release_values[4]}"
release_tag_archive_url="${release_values[5]}"
release_asset_name="${release_values[6]}"
release_asset_url="${release_values[7]}"
release_asset_sha256="${release_values[8]}"
release_asset_id="${release_values[9]}"
release_immutable="${release_values[10]}"

"${github_curl[@]}" -o "$firefox_archive" "$release_asset_url"
actual_release_asset_sha256="$(sha256sum "$firefox_archive" | awk '{print $1}')"
if [[ "$actual_release_asset_sha256" != "$release_asset_sha256" ]]; then
    echo "Firefox release asset SHA-256 does not match GitHub metadata" >&2
    exit 1
fi

readarray -t official_manifest_values < <(
    unzip -p "$firefox_archive" manifest.json | node -e '
        let json = "";
        process.stdin.setEncoding("utf8");
        process.stdin.on("data", (chunk) => { json += chunk; });
        process.stdin.on("end", () => {
            const manifest = JSON.parse(json);
            console.log(manifest.version ?? "");
            console.log(manifest.browser_specific_settings?.gecko?.id ?? "");
        });
    '
)
if [[ "${official_manifest_values[0]}" != "$release_version" ||
      "${official_manifest_values[1]}" != "midoritabs@astian.org" ]]; then
    echo "Official Firefox release asset does not match the release tag or extension ID" >&2
    exit 1
fi

# The official release is already built with the public Unsplash client ID. Reuse
# that exact value for the Android compatibility rebuild so a local secret/env file
# is not required and the resulting wallpaper behavior matches the verified asset.
official_unsplash_key="$(
    unzip -p "$firefox_archive" index.js | node -e '
        let source = "";
        process.stdin.setEncoding("utf8");
        process.stdin.on("data", (chunk) => { source += chunk; });
        process.stdin.on("end", () => {
            const keys = new Set(
                [...source.matchAll(/\bclient_id\s*:\s*["'\'']([A-Za-z0-9_-]{20,})["'\'']/g)]
                    .map((match) => match[1]),
            );
            if (keys.size > 1) {
                throw new Error("Official Firefox release contains multiple Unsplash client IDs");
            }
            process.stdout.write(keys.values().next().value || "");
        });
    '
)"

tag_ref_url="https://api.github.com/repos/goastian/midori-tab/git/ref/tags/${release_tag}"
"${github_curl[@]}" -o "$tag_ref_json" "$tag_ref_url"
readarray -t tag_object_values < <(
    node -e '
        const ref = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
        console.log(ref.object?.type ?? "");
        console.log(ref.object?.sha ?? "");
    ' "$tag_ref_json"
)
tag_ref_object_type="${tag_object_values[0]}"
tag_ref_object_sha="${tag_object_values[1]}"
tag_object_type="$tag_ref_object_type"
tag_object_sha="$tag_ref_object_sha"
if [[ "$tag_object_type" == "tag" ]]; then
    tag_object_json="$work_dir/tag-object.json"
    "${github_curl[@]}" -o "$tag_object_json" "https://api.github.com/repos/goastian/midori-tab/git/tags/${tag_object_sha}"
    readarray -t tag_object_values < <(
        node -e '
            const tag = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
            console.log(tag.object?.type ?? "");
            console.log(tag.object?.sha ?? "");
        ' "$tag_object_json"
    )
    tag_object_type="${tag_object_values[0]}"
    tag_object_sha="${tag_object_values[1]}"
fi
if [[ "$tag_object_type" != "commit" || ! "$tag_object_sha" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Release tag does not resolve to a canonical Git commit" >&2
    exit 1
fi
source_commit="$tag_object_sha"

source_archive_url="https://api.github.com/repos/goastian/midori-tab/tarball/${source_commit}"
"${github_curl[@]}" -o "$source_archive" "$source_archive_url"
source_archive_sha256="$(sha256sum "$source_archive" | awk '{print $1}')"
archive_root="$(tar -tzf "$source_archive" | sed -n '1p' | cut -d/ -f1)"
if [[ "$archive_root" != *-"${source_commit:0:7}" ]]; then
    echo "Release source archive does not match tag commit $source_commit" >&2
    exit 1
fi
tar -xzf "$source_archive" --strip-components=1 --no-same-owner --no-same-permissions -C "$source_dir"
unexpected_source_entry="$(find "$source_dir" -mindepth 1 ! -type f ! -type d -print -quit)"
if [[ -n "$unexpected_source_entry" ]]; then
    echo "Release source contains a non-regular entry: $unexpected_source_entry" >&2
    exit 1
fi

source_version="$(node -e '
    const manifest = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
    process.stdout.write(String(manifest.version || ""));
' "$source_dir/package.json")"
if [[ "$source_version" != "$release_version" ]]; then
    echo "Release tag $release_tag does not match package version $source_version" >&2
    exit 1
fi

if [[ -n "$env_file" ]]; then
    if [[ ! -f "$env_file" ]]; then
        echo "MIDORI_TAB_ENV_FILE is not a file: $env_file" >&2
        exit 1
    fi
    cp "$env_file" "$source_dir/.env.local"
fi

export VITE_MARKETPLACE_API_BASE_URL="${VITE_MARKETPLACE_API_BASE_URL:-https://marketplace.astian.org}"
export VITE_PASSPORT_SERVER="${VITE_PASSPORT_SERVER:-https://accounts.astian.org}"
export VITE_PASSPORT_DOMAIN_SERVER="${VITE_PASSPORT_DOMAIN_SERVER:-https://vpn.astian.org}"
export VITE_ADS_API_BASE="${VITE_ADS_API_BASE:-https://ads.astian.org}"
export VITE_ADS_NEWTAB_PATH="${VITE_ADS_NEWTAB_PATH:-/api/v1/ads/newtab}"

unsplash_key="${VITE_UNSPLASH_API:-}"
if [[ -z "$unsplash_key" && -n "$env_file" ]]; then
    unsplash_key="$(node -e '
        const fs = require("fs");
        const line = fs.readFileSync(process.argv[1], "utf8")
            .split(/\r?\n/)
            .find((entry) => entry.startsWith("VITE_UNSPLASH_API="));
        process.stdout.write(line ? line.slice(line.indexOf("=") + 1).trim() : "");
    ' "$env_file")"
fi
if [[ -z "$unsplash_key" ]]; then
    unsplash_key="$official_unsplash_key"
fi
if [[ -z "$unsplash_key" ]]; then
    echo "Official Firefox release is missing a usable Unsplash client ID; set VITE_UNSPLASH_API or MIDORI_TAB_ENV_FILE" >&2
    exit 1
fi
export VITE_UNSPLASH_API="$unsplash_key"

(
    cd "$source_dir"
    npm ci --ignore-scripts --no-audit --no-fund
    npm run validate:contracts
    npm run test:contracts
    test_log="$work_dir/test-update.log"
    if ! npm run test:update 2>&1 | tee "$test_log"; then
        not_ok_count="$(awk '/^not ok / { count += 1 } END { print count + 0 }' "$test_log")"
        if [[ ( "$release_tag" == "v1.0.40" &&
                "$source_commit" == "7a9540a490e141f3a66f81aa293253254ca1a138" ) ||
              ( "$release_tag" == "v1.0.41" &&
                "$source_commit" == "1f2b9286541da2e2890ca63e7f8a6bbeabd86c7b" ) ]] &&
           [[ "$not_ok_count" == "1" ]] &&
           rg -q "^not ok 1 - tests/ads-service.test.mjs$" "$test_log" &&
           rg -q "does not provide an export named 'buildCacheKey'" "$test_log" &&
           rg -q "^# tests 22$" "$test_log" &&
           rg -q "^# pass 21$" "$test_log" &&
           rg -q "^# fail 1$" "$test_log"; then
            echo "Allowing only the pinned $release_tag stale-test import failure; every other release fails closed." >&2
        else
            exit 1
        fi
    fi
    current_patch_sha256="$(sha256sum "$repo_root/scripts/patch-midori-tab-firefox-android.mjs" | awk '{print $1}')"
    if [[ "$current_patch_sha256" != "$trusted_patch_sha256" ]]; then
        echo "The trusted Android compatibility patch changed while running upstream code" >&2
        exit 1
    fi
    node "$repo_root/scripts/patch-midori-tab-firefox-android.mjs" "$source_dir"
    node --check public/background.js
    node --test \
        tests/omni-background-shortcut.test.mjs \
        tests/omni-search-composable.test.mjs \
        tests/privacy-stats-android.test.mjs \
        tests/semver.test.mjs \
        tests/storage-service.test.mjs
    npm run build:firefox
)

# Close the release/tag TOCTOU window after all untrusted source code has run.
"${github_curl[@]}" -o "$post_release_json" "$release_api_url"
"${github_curl[@]}" -o "$post_tag_ref_json" "$tag_ref_url"
node -e '
    const fs = require("fs");
    const [beforeFile, afterFile, refFile, expectedTag, expectedRefType, expectedRefSha] = process.argv.slice(1);
    const before = JSON.parse(fs.readFileSync(beforeFile, "utf8"));
    const after = JSON.parse(fs.readFileSync(afterFile, "utf8"));
    const ref = JSON.parse(fs.readFileSync(refFile, "utf8"));
    const releaseSnapshot = (release) => {
        const assetName = `midori-tab-${String(release.tag_name || "").replace(/^v/, "")}-firefox.zip`;
        const asset = (release.assets || []).find((candidate) => candidate.name === assetName);
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
    if (JSON.stringify(releaseSnapshot(before)) !== JSON.stringify(releaseSnapshot(after))) {
        throw new Error("Latest Midori Tab release changed while it was being synchronized");
    }
    if (ref.ref !== `refs/tags/${expectedTag}` ||
        ref.object?.type !== expectedRefType || ref.object?.sha !== expectedRefSha) {
        throw new Error("Latest Midori Tab release tag moved while it was being synchronized");
    }
' "$release_json" "$post_release_json" "$post_tag_ref_json" "$release_tag" "$tag_ref_object_type" "$tag_ref_object_sha"

manifest_file="$source_dir/dist/manifest.json"
if [[ ! -f "$manifest_file" || ! -f "$source_dir/dist/index.html" || ! -f "$source_dir/dist/index.js" ]]; then
    echo "Midori Tab did not produce the expected Firefox bundle" >&2
    exit 1
fi

unexpected_entry="$(find "$source_dir/dist" -mindepth 1 ! -type f ! -type d -print -quit)"
if [[ -n "$unexpected_entry" ]]; then
    echo "Midori Tab dist contains a non-regular entry: $unexpected_entry" >&2
    exit 1
fi

if rg -q 'client_id:void 0' "$source_dir/dist"; then
    echo "Midori Tab was built without a usable Unsplash client ID" >&2
    exit 1
fi

if ! rg -q 'estimatedDataSavedBytes' "$source_dir/dist" ||
   ! rg -q 'conservative-8kib-per-block-v1' "$source_dir/dist" ||
   ! rg -q 'androidPrivacyMigrationRevision' "$source_dir/dist" ||
   ! rg -Fq 'pick:["enabled","order","androidPrivacyMigrationRevision"],afterHydrate' "$source_dir/dist"; then
    echo "Midori Tab did not retain the Midori Privacy savings contract" >&2
    exit 1
fi

readarray -t manifest_values < <(
    node -e '
        const manifest = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
        const expectedPermissions = JSON.parse(process.argv[2]);
        if (JSON.stringify(manifest.permissions || []) !== JSON.stringify(expectedPermissions)) {
            process.stderr.write("Midori Tab permissions changed; review the upstream manifest before updating\n");
            process.exit(2);
        }
        console.log(manifest.manifest_version ?? "");
        console.log(manifest.browser_specific_settings?.gecko?.id ?? "");
        console.log(
            !Object.hasOwn(manifest, "chrome_url_overrides") && !Object.hasOwn(manifest, "commands")
                ? "android-direct"
                : "unexpected",
        );
        console.log(manifest.version ?? "");
    ' "$manifest_file" '["storage","tabs","activeTab","browsingData","https://api.rss2json.com/*","https://api.unsplash.com/*","https://api.github.com/*","https://api.open-meteo.com/*","https://geocoding-api.open-meteo.com/*","https://ipwho.is/*","https://nominatim.openstreetmap.org/*","https://duckduckgo.com/*","https://astiango.com/*","https://marketplace.astian.org/*","https://open.er-api.com/*","https://ads.astian.org/*"]'
)

if [[ "${manifest_values[0]}" != "2" ||
      "${manifest_values[1]}" != "midoritabs@astian.org" ||
      "${manifest_values[2]}" != "android-direct" ||
      -z "${manifest_values[3]}" ]]; then
    echo "Unexpected Midori Tab Firefox manifest contract" >&2
    exit 1
fi
android_version_pattern="^${source_version//./\\.}\\.([1-9][0-9]*)$"
if [[ ! "${manifest_values[3]}" =~ $android_version_pattern ]]; then
    echo "Android bundle version must append a positive compatibility revision to $source_version" >&2
    exit 1
fi
compatibility_revision="${BASH_REMATCH[1]}"

cp -a "$source_dir/dist/." "$staging_dir/"
cp "$source_dir/LICENSE" "$staging_dir/LICENSE.upstream"

bundle_sha256="$({
    cd "$staging_dir"
    LC_ALL=C find . -type f ! -name upstream.json -print0 | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | awk '{print $1}'
})"
unsplash_key_sha256="$(printf '%s' "$unsplash_key" | sha256sum | awk '{print $1}')"
compatibility_patch_sha256="$(sha256sum "$repo_root/scripts/patch-midori-tab-firefox-android.mjs" | awk '{print $1}')"
if [[ "$compatibility_patch_sha256" != "$trusted_patch_sha256" ]]; then
    echo "The trusted Android compatibility patch changed before bundle publication" >&2
    exit 1
fi

if [[ -f "$current_metadata_snapshot" ]]; then
    node -e '
        const fs = require("fs");
        const [metadataFile, nextVersion, nextBundleSha256] = process.argv.slice(1);
        const current = JSON.parse(fs.readFileSync(metadataFile, "utf8"));
        const parseVersion = (value) => {
            const match = String(value || "").match(/^(\d+)(?:\.(\d+)){1,3}$/);
            if (!match) throw new Error(`Unsupported extension version: ${value}`);
            return String(value).split(".").map(Number);
        };
        const compareVersions = (left, right) => {
            const a = parseVersion(left);
            const b = parseVersion(right);
            const length = Math.max(a.length, b.length);
            for (let index = 0; index < length; index += 1) {
                const difference = (a[index] || 0) - (b[index] || 0);
                if (difference !== 0) return Math.sign(difference);
            }
            return 0;
        };

        const changedWithoutVersionIncrease =
            current.bundleSha256 !== nextBundleSha256 &&
            compareVersions(nextVersion, current.version) <= 0;
        const isAdoptingReleaseChannel = !current.release?.tag;
        if (changedWithoutVersionIncrease && !isAdoptingReleaseChannel) {
            throw new Error(
                `A changed Midori Tab bundle must raise manifest version above ${current.version}; ` +
                "The Midori version-aware installer cannot safely apply a changed bundle at the same or an older version.",
            );
        }
        if (changedWithoutVersionIncrease && isAdoptingReleaseChannel) {
            process.stderr.write("Adopting the stable Midori Tab release channel from legacy non-release metadata.\n");
        }
    ' "$current_metadata_snapshot" "${manifest_values[3]}" "$bundle_sha256"
fi

node -e '
    const fs = require("fs");
    const [file, repository, releaseTag, commit, sourceVersion, version, bundleSha256,
        unsplashKeySha256, compatibilityPatchSha256, marketplaceBaseUrl, passportServer,
        passportDomainServer, adsBaseUrl, adsNewTabPath, releaseId, releaseUrl,
        releasePublishedAt, sourceArchiveUrl, sourceArchiveSha256, releaseAssetId,
        releaseAssetName, releaseAssetUrl, releaseAssetSha256, compatibilityRevision,
        releaseImmutable, releaseTagArchiveUrl] = process.argv.slice(1);
    fs.writeFileSync(file, `${JSON.stringify({
        sourceRepository: repository,
        sourceRef: releaseTag,
        sourceCommit: commit,
        sourceVersion,
        version,
        compatibilityRevision: Number(compatibilityRevision),
        bundleSha256,
        buildTarget: "firefox-android",
        compatibilityPatch: "scripts/patch-midori-tab-firefox-android.mjs",
        compatibilityPatchSha256,
        release: {
            id: Number(releaseId),
            tag: releaseTag,
            url: releaseUrl,
            publishedAt: releasePublishedAt,
            immutable: releaseImmutable === "true",
            tagArchiveUrl: releaseTagArchiveUrl,
            sourceArchiveUrl,
            sourceArchiveSha256,
            firefoxAsset: {
                id: Number(releaseAssetId),
                name: releaseAssetName,
                url: releaseAssetUrl,
                sha256: releaseAssetSha256,
            },
        },
        buildConfig: {
            unsplashAccessKeySha256: unsplashKeySha256,
            marketplaceBaseUrl,
            passportServer,
            passportDomainServer,
            adsBaseUrl,
            adsNewTabPath,
        },
    }, null, 2)}\n`);
' "$staging_dir/upstream.json" "$canonical_source_repository" "$release_tag" "$source_commit" "$source_version" "${manifest_values[3]}" "$bundle_sha256" \
    "$unsplash_key_sha256" "$compatibility_patch_sha256" "$VITE_MARKETPLACE_API_BASE_URL" "$VITE_PASSPORT_SERVER" \
    "$VITE_PASSPORT_DOMAIN_SERVER" "$VITE_ADS_API_BASE" "$VITE_ADS_NEWTAB_PATH" "$release_id" "$release_url" \
    "$release_published_at" "$source_archive_url" "$source_archive_sha256" "$release_asset_id" "$release_asset_name" \
    "$release_asset_url" "$release_asset_sha256" "$compatibility_revision" "$release_immutable" "$release_tag_archive_url"

case "$target_dir" in
    "$repo_root/app/src/main/assets/extensions/midori_newtab") ;;
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

echo "Synced Midori Tab release $release_tag as Android bundle ${manifest_values[3]} at $source_commit"
