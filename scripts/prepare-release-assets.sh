#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <release-tag> <new-stage-directory>" >&2
  exit 2
fi

tag_name=$1
stage_dir=$2
if [[ ! "$stage_dir" =~ ^[A-Za-z0-9_./-]+$ || "$stage_dir" == "." || "$stage_dir" == *".."* || "$stage_dir" == /* ]]; then
  echo "stage directory must be a new relative path" >&2
  exit 2
fi
if [[ -e "$stage_dir" ]]; then
  echo "stage directory already exists: $stage_dir" >&2
  exit 1
fi

coordinates=$(python3 scripts/read-maven-coordinates.py) || {
  echo "could not read root Maven coordinates" >&2
  exit 1
}
artifact_id=$(printf '%s\n' "$coordinates" | sed -n '1p')
version=$(printf '%s\n' "$coordinates" | sed -n '2p')
if [[ -z "$artifact_id" || -z "$version" || "$coordinates" == *$'\n'*$'\n'* ]]; then
  echo "could not read exactly two root Maven coordinates" >&2
  exit 1
fi
if [[ "$artifact_id" != "miku-md2html-java" ]]; then
  echo "unexpected Maven artifactId: $artifact_id" >&2
  exit 1
fi
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$ ]]; then
  echo "unexpected Maven version: $version" >&2
  exit 1
fi
if [[ "$tag_name" != "v$version" ]]; then
  echo "release tag $tag_name does not match Maven version $version" >&2
  exit 1
fi

mvn -f pom.xml package

runtime_source="target/${artifact_id}-${version}.jar"
sources_source="target/${artifact_id}-${version}-sources.jar"
dist_source="target/${artifact_id}-${version}-dist.zip"
[[ -s "$runtime_source" && -s "$sources_source" && -s "$dist_source" ]]
for entry in \
  'jp/igapyon/mikumd2html/cli/MikuMd2htmlCli.class' \
  'jp/igapyon/mikumd2html/viewer/theme.css' \
  'jp/igapyon/mikumd2html/viewer/viewer.js' \
  'com/vladsch/flexmark/parser/Parser.class' \
  'org/jsoup/Jsoup.class' \
  'org/snakeyaml/engine/v2/api/Load.class' \
  'com/helger/css/reader/CSSReader.class' \
  'META-INF/LICENSE' \
  'META-INF/jsoup/LICENSE' \
  'META-INF/THIRD-PARTY-NOTICES.txt'; do
  jar tf "$runtime_source" | grep -Fx "$entry" >/dev/null
done
jar tf "$sources_source" | grep -Fx 'jp/igapyon/mikumd2html/cli/MikuMd2htmlCli.java' >/dev/null

mkdir -p "$stage_dir"
runtime_asset="${stage_dir}/${artifact_id}-${version}.jar"
sources_asset="${stage_dir}/${artifact_id}-sources-${version}.jar"
cp "$runtime_source" "$runtime_asset"
cp "$sources_source" "$sources_asset"
[[ -s "$runtime_asset" && -s "$sources_asset" ]]
hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}
runtime_sha=$(hash_file "$runtime_asset")
sources_sha=$(hash_file "$sources_asset")
printf 'runtime_sha256=%s\n' "$runtime_sha"
printf 'sources_sha256=%s\n' "$sources_sha"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    printf 'version=%s\n' "$version"
    printf 'runtime_asset=%s\n' "$runtime_asset"
    printf 'sources_asset=%s\n' "$sources_asset"
    printf 'runtime_asset_name=%s\n' "${artifact_id}-${version}.jar"
    printf 'sources_asset_name=%s\n' "${artifact_id}-sources-${version}.jar"
  } >> "$GITHUB_OUTPUT"
fi
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    printf '## Release assets staged\n\n'
    printf '| Asset | SHA-256 |\n| --- | --- |\n'
    printf '| `%s` | `%s` |\n' "${artifact_id}-${version}.jar" "$runtime_sha"
    printf '| `%s` | `%s` |\n' "${artifact_id}-sources-${version}.jar" "$sources_sha"
  } >> "$GITHUB_STEP_SUMMARY"
fi
