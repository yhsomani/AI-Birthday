#!/bin/sh
set -eu

version='2.6.1'
archive_sha256='3440825f514f537c6a96bcf5de995780c2a4a7f83a43208fdc95d4f1fecfad3b'
reviewed_tree_sha256='a133cb76b324bfcb6d018eb320174da0ed9ff03c7a7fa2c32eede0010dc069a9'
archive_url="https://github.com/mobile-dev-inc/Maestro/releases/download/cli-${version}/maestro.zip"

if [ "${1:-}" = '--print-config' ]; then
  printf 'version=%s\nurl=%s\nsha256=%s\ntree_sha256=%s\n' \
    "$version" "$archive_url" "$archive_sha256" "$reviewed_tree_sha256"
  exit 0
fi

destination=${1:-"${TMPDIR:-/tmp}/birthday-autopilot-maestro-${version}"}
binary="${destination}/maestro/bin/maestro"
receipt="${destination}/.verified-release"
state_home="${TMPDIR:-/tmp}/birthday-autopilot-maestro-state-${version}"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  echo 'error: JAVA_HOME must point to a Java 17 or newer runtime' >&2
  exit 1
fi
java_major=$(
  "${JAVA_HOME}/bin/java" -version 2>&1 |
    sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p'
)
if [ -z "$java_major" ] || [ "$java_major" -lt 17 ]; then
  echo 'error: Maestro requires Java 17 or newer' >&2
  exit 1
fi

for command_name in curl shasum unzip awk sed wc mktemp find sort; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "error: required installer command is unavailable: ${command_name}" >&2
    exit 1
  }
done

workspace=$(mktemp -d "${TMPDIR:-/tmp}/birthday-maestro.XXXXXX")
trap 'rm -rf "$workspace"' EXIT HUP INT TERM

calculate_tree_sha256() {
  tree_root=$1
  manifest_file=$2
  unsupported_file="${workspace}/unsupported-entries"
  paths_file="${workspace}/tree-paths"
  sorted_paths_file="${workspace}/tree-paths-sorted"

  if [ ! -d "$tree_root" ]; then
    echo 'error: Maestro installation tree is missing' >&2
    return 1
  fi
  find "$tree_root" ! -type d ! -type f -print > "$unsupported_file"
  if [ -s "$unsupported_file" ]; then
    echo 'error: Maestro installation tree contains a symlink or unsupported entry' >&2
    return 1
  fi
  find "$tree_root" -type f -print > "$paths_file"
  LC_ALL=C sort "$paths_file" > "$sorted_paths_file"
  : > "$manifest_file"
  while IFS= read -r file_path; do
    relative_path=${file_path#"$tree_root"/}
    file_sha256=$(shasum -a 256 "$file_path" | awk '{ print $1 }')
    printf '%s  %s\n' "$file_sha256" "$relative_path" >> "$manifest_file"
  done < "$sorted_paths_file"
  if [ ! -s "$manifest_file" ]; then
    echo 'error: Maestro installation tree contains no regular files' >&2
    return 1
  fi
  shasum -a 256 "$manifest_file" | awk '{ print $1 }'
}

if [ -x "$binary" ]; then
  if [ ! -f "$receipt" ]; then
    echo 'error: existing Maestro installation has no verification receipt' >&2
    exit 1
  fi
  receipt_version=$(sed -n 's/^version=//p' "$receipt")
  receipt_archive_sha256=$(sed -n 's/^archive_sha256=//p' "$receipt")
  receipt_binary_sha256=$(sed -n 's/^binary_sha256=//p' "$receipt")
  receipt_tree_sha256=$(sed -n 's/^tree_sha256=//p' "$receipt")
  actual_binary_sha256=$(shasum -a 256 "$binary" | awk '{ print $1 }')
  actual_tree_sha256=$(calculate_tree_sha256 \
    "${destination}/maestro" "${workspace}/existing-tree-manifest")
  if [ "$receipt_version" != "$version" ] ||
    [ "$receipt_archive_sha256" != "$archive_sha256" ] ||
    [ "$receipt_binary_sha256" != "$actual_binary_sha256" ] ||
    [ "$receipt_tree_sha256" != "$reviewed_tree_sha256" ] ||
    [ "$actual_tree_sha256" != "$reviewed_tree_sha256" ]; then
    echo 'error: existing Maestro installation does not match its verified release receipt' >&2
    exit 1
  fi
  mkdir -p "$state_home/.maestro"
  installed_version=$(
    HOME="$state_home" MAESTRO_OPTS="-Duser.home=$state_home" \
      MAESTRO_CLI_NO_ANALYTICS=true MAESTRO_DISABLE_UPDATE_CHECK=true \
      MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
      "$binary" --version 2>/dev/null |
      sed -n 's/[^0-9]*\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\).*/\1/p' |
      sed -n '1p'
  )
  if [ "$installed_version" != "$version" ]; then
    echo 'error: existing Maestro installation does not match the pinned version' >&2
    exit 1
  fi
  printf '%s\n' "$binary"
  exit 0
fi

if [ -e "$destination" ]; then
  echo 'error: refusing to overlay an unverified Maestro destination' >&2
  exit 1
fi

archive="${workspace}/maestro.zip"

curl \
  --proto '=https' \
  --tlsv1.2 \
  --fail \
  --location \
  --retry 3 \
  --max-filesize 419430400 \
  --silent \
  --show-error \
  --output "$archive" \
  "$archive_url"

archive_bytes=$(wc -c < "$archive" | awk '{ print $1 }')
if [ "$archive_bytes" -lt 1048576 ] || [ "$archive_bytes" -gt 419430400 ]; then
  echo 'error: Maestro archive size is outside the reviewed bounds' >&2
  exit 1
fi

actual_sha256=$(shasum -a 256 "$archive" | awk '{ print $1 }')
if [ "$actual_sha256" != "$archive_sha256" ]; then
  echo 'error: Maestro archive SHA-256 does not match the reviewed release' >&2
  exit 1
fi

if ! unzip -Z1 "$archive" | awk '
  BEGIN { valid = 1; count = 0 }
  {
    count += 1
    if ($0 !~ /^maestro\// || $0 ~ /(^|\/)\.\.($|\/)/ || $0 ~ /^\//) valid = 0
  }
  END { exit !(valid && count > 0 && count < 20000) }
'; then
  echo 'error: Maestro archive contains an invalid path or entry count' >&2
  exit 1
fi

mkdir -p "$destination"
unzip -q "$archive" -d "$destination"
if [ ! -x "$binary" ]; then
  echo 'error: Maestro executable is missing after verified extraction' >&2
  exit 1
fi

mkdir -p "$state_home/.maestro"
installed_version=$(
  HOME="$state_home" MAESTRO_OPTS="-Duser.home=$state_home" \
    MAESTRO_CLI_NO_ANALYTICS=true MAESTRO_DISABLE_UPDATE_CHECK=true \
    MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "$binary" --version 2>/dev/null |
    sed -n 's/[^0-9]*\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\).*/\1/p' |
    sed -n '1p'
)
if [ "$installed_version" != "$version" ]; then
  echo 'error: extracted Maestro executable reports an unexpected version' >&2
  exit 1
fi

binary_sha256=$(shasum -a 256 "$binary" | awk '{ print $1 }')
tree_sha256=$(calculate_tree_sha256 \
  "${destination}/maestro" "${workspace}/extracted-tree-manifest")
if [ "$tree_sha256" != "$reviewed_tree_sha256" ]; then
  echo 'error: extracted Maestro tree does not match the reviewed release' >&2
  exit 1
fi
printf 'version=%s\narchive_sha256=%s\nbinary_sha256=%s\ntree_sha256=%s\n' \
  "$version" "$archive_sha256" "$binary_sha256" "$tree_sha256" > "$receipt"

printf '%s\n' "$binary"
