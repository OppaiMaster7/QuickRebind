#!/usr/bin/env bash
#
# Publishes the built jars to Modrinth, one version at a time.
#
#   MODRINTH_TOKEN=... tools/publish.sh --dry-run     show what would go up
#   MODRINTH_TOKEN=... tools/publish.sh               publish every version
#   MODRINTH_TOKEN=... tools/publish.sh --only 1.20.1
#
# Publishing is public and awkward to take back, so this refuses to run until
# tools/verify.sh has passed and asks for confirmation before the first upload.
# Nothing here happens by accident: `build` never triggers it, and there is no
# CI job that calls it.
#
# The version number sent to Modrinth is <mod_version>+<minecraft_version>,
# built in each versions/<v>/build.gradle. Release notes come from CHANGELOG.md.
#
# Get a token at https://modrinth.com/settings/pats — it needs "Create versions".
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
ROOT="$PWD"

DRY_RUN=0
ONLY=""

while [ $# -gt 0 ]; do
	case "$1" in
		--dry-run) DRY_RUN=1 ;;
		--only) ONLY="$2"; shift ;;
		-h|--help) awk 'NR > 1 { if (!/^#/) exit; sub(/^# ?/, ""); print }' "$0"; exit 0 ;;
		*) echo "unknown option: $1" >&2; exit 2 ;;
	esac
	shift
done

if [ -z "${MODRINTH_TOKEN:-}" ]; then
	echo "MODRINTH_TOKEN is not set — create one at https://modrinth.com/settings/pats" >&2
	exit 2
fi

MATRIX="$(mktemp)"
trap 'rm -f "$MATRIX"' EXIT
grep -vE '^[[:space:]]*(#|$)' tools/versions.tsv > "$MATRIX"

# ------------------------------------------------------------------ preflight

echo "About to publish to Modrinth:"
echo

while IFS=$'\t' read -r MC BUILD_JDK TARGET_JAVA WRAPPER <&3; do
	[ -z "$MC" ] && continue
	[ -n "$ONLY" ] && [ "$ONLY" != "$MC" ] && continue

	MOD_VERSION="$(sed -n 's/^mod_version=//p' "versions/$MC/gradle.properties" | tr -d '\r')"
	JAR="versions/$MC/build/libs/quickrebind-$MC-$MOD_VERSION.jar"

	if [ -f "$JAR" ]; then
		printf '  %-9s %s+%s  (%s KB)\n' "$MC" "$MOD_VERSION" "$MC" "$(( $(stat -c%s "$JAR") / 1024 ))"
	else
		printf '  %-9s MISSING — run tools/verify.sh first\n' "$MC"
		exit 1
	fi
done 3< "$MATRIX"

echo
echo "Release notes come from CHANGELOG.md:"
head -3 CHANGELOG.md | sed 's/^/  /'
echo

if [ "$DRY_RUN" = 1 ]; then
	echo "--dry-run: stopping here, nothing uploaded."
	exit 0
fi

# A published version is visible immediately and mails everyone watching the
# project, so make the last step deliberate rather than a consequence of
# scrolling back and hitting return on an old command.
printf 'Type "publish" to upload these: '
read -r CONFIRM

if [ "$CONFIRM" != "publish" ]; then
	echo "Nothing uploaded."
	exit 1
fi

# -------------------------------------------------------------------- upload

FAILED=0

while IFS=$'\t' read -r MC BUILD_JDK TARGET_JAVA WRAPPER <&3; do
	[ -z "$MC" ] && continue
	[ -n "$ONLY" ] && [ "$ONLY" != "$MC" ] && continue

	if [ "$WRAPPER" = root ]; then
		GRADLEW="$ROOT/gradlew"
	else
		GRADLEW="$ROOT/versions/$MC/gradlew"
	fi

	JDK=""
	for CANDIDATE in "/c/Program Files/Microsoft/jdk-${BUILD_JDK}"* \
			"/usr/lib/jvm/temurin-${BUILD_JDK}-jdk"*; do
		[ -x "$CANDIDATE/bin/java" ] && JDK="$CANDIDATE" && break
	done

	echo
	echo "== $MC =="

	if "$GRADLEW" -p "versions/$MC" modrinth --console=plain \
			${JDK:+-Dorg.gradle.java.home="$JDK"} < /dev/null; then
		echo "   published"
	else
		echo "   FAILED"
		FAILED=$((FAILED + 1))
	fi
done 3< "$MATRIX"

echo
if [ "$FAILED" -eq 0 ]; then
	echo "All uploaded. Check https://modrinth.com/mod/quickrebind/versions"
else
	echo "$FAILED version(s) failed — nothing is rolled back, so check what did go up."
fi

[ "$FAILED" -eq 0 ]
