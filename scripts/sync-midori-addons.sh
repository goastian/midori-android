#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_root="$(mktemp -d)"

cleanup() {
    rm -rf "$work_root"
}
trap cleanup EXIT

require_commands() {
    local command_name
    for command_name in curl jq node sha256sum unzip zipinfo; do
        if ! command -v "$command_name" >/dev/null 2>&1; then
            echo "Missing required command: $command_name" >&2
            exit 1
        fi
    done
}

configure_addon() {
    addon="$1"

    case "$addon" in
        midori-tab)
            repository="goastian/midori-tab"
            target_dir="$repo_root/app/src/main/assets/extensions/midori_newtab"
            patch_script="$repo_root/scripts/patch-midori-tab-firefox-android.mjs"
            extension_id="midoritabs@astian.org"
            asset_name_prefix="midori-tab-"
            asset_name_suffix="-firefox.zip"
            required_files=(manifest.json index.html index.js background.js)
            ;;
        midori-privacy)
            repository="goastian/midori-privacy"
            target_dir="$repo_root/app/src/main/assets/extensions/midori_privacy"
            patch_script="$repo_root/scripts/patch-midori-privacy-firefox-android.mjs"
            extension_id="midori-protection@astian.org"
            asset_name_prefix="midori-privacy-"
            asset_name_suffix="-firefox.zip"
            required_files=(manifest.json background.html js/start.js js/midori-stats.js LICENSE.txt)
            ;;
        midori-vpn)
            repository="goastian/midorivpn-extension"
            target_dir="$repo_root/app/src/main/assets/extensions/midori_vpn"
            patch_script="$repo_root/scripts/patch-midori-vpn-firefox-android.mjs"
            extension_id="midorivpn@astian.org"
            asset_name_prefix="midorivpn-extension-"
            asset_name_suffix=".zip"
            required_files=(manifest.json background.js popup.html icons/icon64.png)
            ;;
        *)
            echo "Unknown addon: $addon" >&2
            echo "Usage: $0 [midori-tab] [midori-privacy] [midori-vpn]" >&2
            exit 1
            ;;
    esac

    repository_url="https://github.com/$repository"
    api_url="https://api.github.com/repos/$repository"
    addon_work_dir="$work_root/$addon"
    archive="$addon_work_dir/release.zip"
    staging_dir="$addon_work_dir/extension"
    metadata_file="$staging_dir/upstream.json"
    mkdir -p "$staging_dir"
}

read_latest_release() {
    local release_json="$addon_work_dir/release.json"
    curl --fail --location --silent --show-error \
        --retry 3 --retry-delay 2 \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        -o "$release_json" \
        "$api_url/releases/latest"

    if [[ "$(jq -r '.draft or .prerelease' "$release_json")" != "false" ]]; then
        echo "$addon latest release is not stable" >&2
        exit 1
    fi

    release_tag="$(jq -er '.tag_name' "$release_json")"
    if [[ ! "$release_tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "Unexpected $addon release tag: $release_tag" >&2
        exit 1
    fi

    source_version="${release_tag#v}"
    asset_name="$asset_name_prefix$source_version$asset_name_suffix"
    asset_url="$(jq -er --arg name "$asset_name" \
        '.assets[] | select(.name == $name) | .browser_download_url' "$release_json")"
    asset_sha256="$(jq -er --arg name "$asset_name" \
        '.assets[] | select(.name == $name) | .digest' "$release_json" | sed 's/^sha256://')"

    expected_asset_url="$repository_url/releases/download/$release_tag/$asset_name"
    if [[ "$asset_url" != "$expected_asset_url" || ! "$asset_sha256" =~ ^[0-9a-f]{64}$ ]]; then
        echo "Invalid Firefox asset metadata for $addon $release_tag" >&2
        exit 1
    fi
}

download_and_extract_release() {
    echo "Downloading $addon $release_tag"
    curl --fail --location --silent --show-error \
        --retry 3 --retry-delay 2 \
        -o "$archive" \
        "$asset_url"

    actual_sha256="$(sha256sum "$archive" | awk '{print $1}')"
    if [[ "$actual_sha256" != "$asset_sha256" ]]; then
        echo "$addon Firefox ZIP checksum does not match GitHub" >&2
        exit 1
    fi

    unsafe_entry="$(zipinfo -1 "$archive" | node -e '
        let input = "";
        process.stdin.setEncoding("utf8");
        process.stdin.on("data", chunk => { input += chunk; });
        process.stdin.on("end", () => {
            const unsafe = input.split(/\r?\n/).find(entry =>
                entry.startsWith("/") ||
                entry.includes("\\\\") ||
                entry.split("/").includes("..")
            );
            process.stdout.write(unsafe || "");
        });
    ')"
    if [[ -n "$unsafe_entry" ]]; then
        echo "Unsafe path in $addon Firefox ZIP: $unsafe_entry" >&2
        exit 1
    fi

    unzip -qq "$archive" -d "$staging_dir"
    unexpected_entry="$(find "$staging_dir" -mindepth 1 ! -type f ! -type d -print -quit)"
    if [[ -n "$unexpected_entry" ]]; then
        echo "Unsupported entry in $addon Firefox ZIP: $unexpected_entry" >&2
        exit 1
    fi
}

validate_release_files() {
    local relative_path
    for relative_path in "${required_files[@]}"; do
        if [[ ! -f "$staging_dir/$relative_path" ]]; then
            echo "$addon Firefox ZIP is missing $relative_path" >&2
            exit 1
        fi
    done

    official_version="$(jq -er '.version' "$staging_dir/manifest.json")"
    official_id="$(jq -er \
        '.browser_specific_settings.gecko.id // .applications.gecko.id' \
        "$staging_dir/manifest.json")"
    if [[ "$official_version" != "$source_version" || "$official_id" != "$extension_id" ]]; then
        echo "$addon manifest does not match release $release_tag" >&2
        exit 1
    fi
}

apply_android_compatibility() {
    node "$patch_script" "$staging_dir"

    android_version="$(jq -er '.version' "$staging_dir/manifest.json")"
    compatibility_revision="${android_version##*.}"
    if [[ "$android_version" != "$source_version.$compatibility_revision" ||
          ! "$compatibility_revision" =~ ^[1-9][0-9]*$ ]]; then
        echo "$addon compatibility patch produced invalid version $android_version" >&2
        exit 1
    fi

    if [[ "$addon" == "midori-tab" ]]; then
        node --check "$staging_dir/background.js"
        curl --fail --location --silent --show-error \
            -o "$staging_dir/LICENSE.upstream" \
            "https://raw.githubusercontent.com/$repository/$release_tag/LICENSE"
    elif [[ "$addon" == "midori-privacy" ]]; then
        node --check "$staging_dir/js/midori-stats.js"
        cp "$staging_dir/LICENSE.txt" "$staging_dir/LICENSE.upstream"
    else
        node --check "$staging_dir/background.js"
        curl --fail --location --silent --show-error \
            -o "$staging_dir/LICENSE.upstream" \
            "https://raw.githubusercontent.com/$repository/$release_tag/LICENSE"
    fi
}

write_metadata() {
    bundle_sha256="$({
        cd "$staging_dir"
        LC_ALL=C find . -type f ! -name upstream.json -print0 |
            LC_ALL=C sort -z |
            xargs -0 sha256sum |
            sha256sum |
            awk '{print $1}'
    })"

    jq -n \
        --arg sourceRepository "$repository_url" \
        --arg sourceVersion "$source_version" \
        --arg version "$android_version" \
        --argjson compatibilityRevision "$compatibility_revision" \
        --arg bundleSha256 "$bundle_sha256" \
        --arg tag "$release_tag" \
        --arg releaseUrl "$repository_url/releases/tag/$release_tag" \
        --arg assetName "$asset_name" \
        --arg assetUrl "$asset_url" \
        --arg assetSha256 "$asset_sha256" \
        '{
            sourceRepository: $sourceRepository,
            sourceVersion: $sourceVersion,
            version: $version,
            compatibilityRevision: $compatibilityRevision,
            bundleSha256: $bundleSha256,
            release: {
                tag: $tag,
                url: $releaseUrl,
                firefoxAsset: {
                    name: $assetName,
                    url: $assetUrl,
                    sha256: $assetSha256
                }
            }
        }' > "$metadata_file"
}

publish_addon() {
    if [[ -f "$target_dir/upstream.json" ]]; then
        node "$repo_root/scripts/verify-midori-addon-update.mjs" \
            "$addon" \
            "$target_dir/upstream.json" \
            "$metadata_file"
    fi

    rm -rf "$target_dir"
    mkdir -p "$(dirname "$target_dir")"
    mv "$staging_dir" "$target_dir"
    echo "Synced $addon $release_tag as Android bundle $android_version"
}

sync_addon() {
    configure_addon "$1"
    read_latest_release
    download_and_extract_release
    validate_release_files
    apply_android_compatibility
    write_metadata
    publish_addon
}

require_commands

if [[ "$#" -eq 0 ]]; then
    requested_addons=(midori-tab midori-privacy)
else
    requested_addons=("$@")
fi

for requested_addon in "${requested_addons[@]}"; do
    sync_addon "$requested_addon"
done
