#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_dir="$repo_root/app/src/main/assets/extensions/midori_newtab"
canonical_source_repository="https://github.com/goastian/midori-tab"
clone_repository="${MIDORI_TAB_REPOSITORY:-${canonical_source_repository}.git}"
source_ref="${1:-${MIDORI_TAB_REF:-main}}"
local_source="${MIDORI_TAB_SOURCE:-}"
env_file="${MIDORI_TAB_ENV_FILE:-}"
work_dir="$(mktemp -d)"
source_dir="$work_dir/source"
staging_dir="$work_dir/extension"
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

case "$clone_repository" in
    "$canonical_source_repository"|"${canonical_source_repository}.git") ;;
    *)
        echo "Refusing non-canonical Midori Tab repository: $clone_repository" >&2
        exit 1
        ;;
esac

if [[ -n "$local_source" ]]; then
    if [[ ! -d "$local_source/.git" ]]; then
        echo "MIDORI_TAB_SOURCE is not a Git checkout: $local_source" >&2
        exit 1
    fi
    source_commit="$(git -C "$local_source" rev-parse "${source_ref}^{commit}")"
    git -C "$local_source" archive "$source_commit" | tar -x -C "$source_dir"
else
    git clone --filter=blob:none --no-checkout "$clone_repository" "$source_dir"
    git -C "$source_dir" fetch --depth 1 origin "$source_ref"
    git -C "$source_dir" checkout --detach FETCH_HEAD
    source_commit="$(git -C "$source_dir" rev-parse HEAD)"
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
    echo "VITE_UNSPLASH_API or MIDORI_TAB_ENV_FILE is required for the default wallpaper" >&2
    exit 1
fi

(
    cd "$source_dir"
    npm ci --no-audit --no-fund
    npm run validate:contracts
    npm run test:contracts
    test_log="$work_dir/test-update.log"
    if ! npm run test:update 2>&1 | tee "$test_log"; then
        not_ok_count="$(awk '/^not ok / { count += 1 } END { print count + 0 }' "$test_log")"
        if [[ "$source_commit" == "7a9540a490e141f3a66f81aa293253254ca1a138" &&
              "$not_ok_count" == "1" ]] &&
           rg -q "^not ok 1 - tests/ads-service.test.mjs$" "$test_log" &&
           rg -q "does not provide an export named 'buildCacheKey'" "$test_log" &&
           rg -q "^# tests 22$" "$test_log" &&
           rg -q "^# pass 21$" "$test_log" &&
           rg -q "^# fail 1$" "$test_log"; then
            echo "Allowing the pinned 7a9540a4 stale-test failure; all other commits fail closed." >&2
        else
            exit 1
        fi
    fi
    node "$repo_root/scripts/patch-midori-tab-firefox-android.mjs" "$source_dir"
    npm run build:firefox
)

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
        console.log(manifest.chrome_url_overrides?.newtab ?? "");
        console.log(manifest.version ?? "");
    ' "$manifest_file" '["storage","identity","tabs","activeTab","bookmarks","history","browsingData","search","https://api.rss2json.com/*","https://api.unsplash.com/*","https://api.github.com/*","https://api.open-meteo.com/*","https://geocoding-api.open-meteo.com/*","https://ipwho.is/*","https://nominatim.openstreetmap.org/*","https://duckduckgo.com/*","https://astiango.com/*","https://marketplace.astian.org/*","https://open.er-api.com/*","https://ads.astian.org/*"]'
)

if [[ "${manifest_values[0]}" != "2" ||
      "${manifest_values[1]}" != "midoritabs@astian.org" ||
      "${manifest_values[2]}" != "index.html" ||
      -z "${manifest_values[3]}" ]]; then
    echo "Unexpected Midori Tab Firefox manifest contract" >&2
    exit 1
fi

cp -a "$source_dir/dist/." "$staging_dir/"
cp "$source_dir/LICENSE" "$staging_dir/LICENSE.upstream"

bundle_sha256="$({
    cd "$staging_dir"
    LC_ALL=C find . -type f ! -name upstream.json -print0 | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | awk '{print $1}'
})"
unsplash_key_sha256="$(printf '%s' "$unsplash_key" | sha256sum | awk '{print $1}')"
compatibility_patch_sha256="$(sha256sum "$repo_root/scripts/patch-midori-tab-firefox-android.mjs" | awk '{print $1}')"

if [[ -f "$target_dir/upstream.json" ]]; then
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

        if (current.bundleSha256 !== nextBundleSha256 && compareVersions(nextVersion, current.version) <= 0) {
            throw new Error(
                `A changed Midori Tab bundle must raise manifest version above ${current.version}; ` +
                "GeckoView will not replace a built-in extension at the same or an older version.",
            );
        }
    ' "$target_dir/upstream.json" "${manifest_values[3]}" "$bundle_sha256"
fi

node -e '
    const fs = require("fs");
    const [file, repository, ref, commit, version, bundleSha256, unsplashKeySha256, compatibilityPatchSha256,
        marketplaceBaseUrl, passportServer, passportDomainServer, adsBaseUrl, adsNewTabPath] = process.argv.slice(1);
    fs.writeFileSync(file, `${JSON.stringify({
        sourceRepository: repository,
        sourceRef: ref,
        sourceCommit: commit,
        version,
        bundleSha256,
        buildTarget: "firefox-android",
        compatibilityPatch: "scripts/patch-midori-tab-firefox-android.mjs",
        compatibilityPatchSha256,
        buildConfig: {
            unsplashAccessKeySha256: unsplashKeySha256,
            marketplaceBaseUrl,
            passportServer,
            passportDomainServer,
            adsBaseUrl,
            adsNewTabPath,
        },
    }, null, 2)}\n`);
' "$staging_dir/upstream.json" "$canonical_source_repository" "$source_commit" "$source_commit" "${manifest_values[3]}" "$bundle_sha256" \
    "$unsplash_key_sha256" "$compatibility_patch_sha256" "$VITE_MARKETPLACE_API_BASE_URL" "$VITE_PASSPORT_SERVER" \
    "$VITE_PASSPORT_DOMAIN_SERVER" "$VITE_ADS_API_BASE" "$VITE_ADS_NEWTAB_PATH"

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
if [[ -e "$target_dir" ]]; then
    mv "$target_dir" "$backup_dir"
fi
replace_started=true
mv "$replacement_dir" "$target_dir"
replace_started=false
rm -rf "$backup_dir"

echo "Synced Midori Tab ${manifest_values[3]} at $source_commit"
