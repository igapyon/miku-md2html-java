#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <runtime-jar> <expected-version>" >&2
  exit 2
fi

runtime_jar=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
expected_version=$2
if [[ ! -s "$runtime_jar" ]]; then
  echo "runtime JAR is missing or empty: $runtime_jar" >&2
  exit 1
fi

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
mkdir "$work_dir/isolated"
cp "$runtime_jar" "$work_dir/isolated/runtime.jar"
cd "$work_dir/isolated"

java -jar runtime.jar --version >version.stdout 2>version.stderr
[[ "$(cat version.stdout)" == "miku-md2html $expected_version" ]]
[[ ! -s version.stderr ]]

java -jar runtime.jar --help >help.stdout 2>help.stderr
[[ ! -s help.stderr ]]
grep -F -- "java -jar miku-md2html-java-$expected_version.jar <input.md> --out <output.html>" help.stdout >/dev/null
grep -F -- "--report-json <file>" help.stdout >/dev/null

cat > input.md <<'MARKDOWN'
# Packaged smoke

![pixel](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/odcAAAAASUVORK5CYII=)
MARKDOWN

java -jar runtime.jar input.md --out output.html --report-json report.json >convert.stdout 2>convert.stderr
[[ ! -s convert.stderr ]]
grep -F -- '<h1 id="miku-h-packaged-smoke">Packaged smoke</h1>' output.html >/dev/null
grep -F -- '<img src="data:image/png;base64,' output.html >/dev/null
python3 -c 'import json,sys; d=json.load(open("report.json", encoding="utf-8")); assert d["success"] is True; assert d["tool"]["version"] == sys.argv[1]; assert d["output"]["written"] is True; assert d["stats"]["imageOccurrences"] == 1; assert d["stats"]["uniqueAssets"] == 1' "$expected_version"

cp output.html output.before.html
set +e
java -jar runtime.jar input.md --out output.html >retry.stdout 2>retry.stderr
retry_status=$?
set -e
[[ $retry_status -eq 1 ]]
cmp -s output.before.html output.html
grep -F -- 'E_OUTPUT_EXISTS' retry.stderr >/dev/null

echo "Packaged runtime smoke passed for Java $(java -version 2>&1 | head -n 1)"
