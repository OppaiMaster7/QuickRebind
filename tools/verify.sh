#!/usr/bin/env bash
#
# Builds and checks every supported Minecraft version.
#
#   tools/verify.sh                  core tests, then build + check every jar
#   tools/verify.sh --core           just the core unit tests (a second or two)
#   tools/verify.sh --no-core        skip them; what CI uses per version
#   tools/verify.sh --only 1.20.1    one version
#   tools/verify.sh --selftest       also launch each version and let the mod
#                                    test itself against that game's keybinds
#
# Four layers, and they catch different things. The core tests cover the rules
# against a fake install and need no Minecraft at all. Building proves each
# version's GUI port still compiles against its own API. The jar checks read
# what actually came out — the file players download. And the self-test is the
# only one that touches a real KeyMapping, which is the part that genuinely
# differs per version and the part no other layer can reach.
#
# The version matrix lives in tools/versions.tsv; adding a Minecraft version is
# one line there.
#
# Runs on Git Bash and on CI. Set JDK21_HOME / JDK25_HOME to skip detection.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
ROOT="$PWD"

CORE_ONLY=0
SKIP_CORE=0
RUN_SELFTEST=0
ONLY=""

while [ $# -gt 0 ]; do
	case "$1" in
		--core) CORE_ONLY=1 ;;
		--no-core) SKIP_CORE=1 ;;
		--selftest) RUN_SELFTEST=1 ;;
		--only) ONLY="$2"; shift ;;
		# Print the comment header above and stop at the first line of code, so
		# this can't drift out of date or run off the end again.
		-h|--help) awk 'NR > 1 { if (!/^#/) exit; sub(/^# ?/, ""); print }' "$0"; exit 0 ;;
		*) echo "unknown option: $1" >&2; exit 2 ;;
	esac
	shift
done

PASS=0
FAIL=0
SUMMARY=""

green() { printf '\033[32m%s\033[0m' "$1"; }
red() { printf '\033[31m%s\033[0m' "$1"; }

record() { # name outcome detail
	if [ "$2" = ok ]; then
		PASS=$((PASS + 1))
		SUMMARY="${SUMMARY}$(green '  PASS')  $1${3:+  $3}\n"
	else
		FAIL=$((FAIL + 1))
		SUMMARY="${SUMMARY}$(red '  FAIL')  $1${3:+  $3}\n"
	fi
}

heading() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

# --------------------------------------------------------------------- JDKs

# Gradle has to run on a specific JDK per version, so find them once up front
# and fail loudly rather than half way through a build.
find_jdk() { # major
	local major="$1"
	local named="JDK${major}_HOME"
	local value="${!named:-}"

	if [ -n "$value" ] && [ -x "$value/bin/java" ]; then
		echo "$value"
		return 0
	fi

	# What actions/setup-java exports on CI.
	local ci="JAVA_HOME_${major}_X64"
	value="${!ci:-}"

	if [ -n "$value" ] && [ -x "$value/bin/java" ]; then
		echo "$value"
		return 0
	fi

	local candidate
	for candidate in \
		"/c/Program Files/Microsoft/jdk-${major}"* \
		"/c/Program Files/Eclipse Adoptium/jdk-${major}"* \
		"/c/Program Files/Java/jdk-${major}"* \
		"/usr/lib/jvm/temurin-${major}-jdk"* \
		"/usr/lib/jvm/java-${major}-openjdk"*; do
		if [ -x "$candidate/bin/java" ]; then
			echo "$candidate"
			return 0
		fi
	done

	# Last resort: the JDK we are already running on, if it is the right one.
	if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
		if "$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -q "\"${major}\."; then
			echo "$JAVA_HOME"
			return 0
		fi
	fi

	return 1
}

# Not just "is it on PATH": Windows ships a python3 stub that exists, runs, and
# only ever tells you to visit the Microsoft Store, so ask each candidate to
# prove it is an interpreter.
PYTHON=""

for CANDIDATE in python3 python py; do
	if command -v "$CANDIDATE" > /dev/null 2>&1 &&
			"$CANDIDATE" -c 'print("ok")' 2>/dev/null | grep -q ok; then
		PYTHON="$CANDIDATE"
		break
	fi
done

if [ -z "$PYTHON" ]; then
	echo "python is needed for the jar checks" >&2
	exit 2
fi

# ---------------------------------------------------------------- core tests

if [ "$SKIP_CORE" = 0 ]; then
	heading "core"

	if ./gradlew -p core test --console=plain -q < /dev/null; then
		COUNT=$(find core/build/test-results/test -name 'TEST-*.xml' -exec grep -ho 'tests="[0-9]*"' {} \; 2>/dev/null |
			sed 's/[^0-9]//g' | awk '{n += $1} END {print n}')
		record "core unit tests" ok "${COUNT:-?} tests"
	else
		record "core unit tests" fail "see core/build/reports/tests/test/index.html"
	fi
fi

if [ "$CORE_ONLY" = 1 ]; then
	heading "summary"
	printf '%b' "$SUMMARY"
	printf '\n%d passed, %d failed\n' "$PASS" "$FAIL"
	[ "$FAIL" -eq 0 ]
	exit $?
fi

# ------------------------------------------------------------------ versions

# Snapshot the matrix before starting. A full --selftest run takes long enough
# that the file can be edited underneath it, and a half-read line turns into a
# phantom version. Comments and blank lines go here rather than in the loop.
MATRIX="$(mktemp)"
trap 'rm -f "$MATRIX"' EXIT
grep -vE '^[[:space:]]*(#|$)' tools/versions.tsv > "$MATRIX"

# Read it on fd 3, not stdin: Gradle inherits stdin and `runClient` swallows
# whatever is left of it, so a loop reading the list on stdin quietly stops
# after the first version it launches a game for.
while IFS=$'\t' read -r MC BUILD_JDK TARGET_JAVA WRAPPER <&3; do
	[ -z "$MC" ] && continue
	[ -n "$ONLY" ] && [ "$ONLY" != "$MC" ] && continue

	heading "Minecraft $MC"

	DIR="versions/$MC"

	if [ ! -d "$DIR" ]; then
		record "$MC" fail "no versions/$MC directory"
		continue
	fi

	JDK="$(find_jdk "$BUILD_JDK")"

	if [ -z "$JDK" ]; then
		record "$MC build" fail "no JDK $BUILD_JDK found — set JDK${BUILD_JDK}_HOME"
		continue
	fi

	echo "   JDK $BUILD_JDK: $JDK"

	if [ "$WRAPPER" = root ]; then
		GRADLEW="$ROOT/gradlew"
	else
		GRADLEW="$ROOT/$DIR/gradlew"
	fi

	LOG="$ROOT/build/verify/$MC-build.log"
	mkdir -p "$(dirname "$LOG")"

	echo "   building..."

	if "$GRADLEW" -p "$DIR" build --console=plain \
			-Dorg.gradle.java.home="$JDK" < /dev/null > "$LOG" 2>&1; then
		record "$MC build" ok
	else
		record "$MC build" fail "$LOG"
		tail -25 "$LOG" | sed 's/^/      /'
		continue
	fi

	# Ask the build what it just produced rather than assuming a version. An old
	# jar left in build/libs after a version bump is otherwise indistinguishable
	# from the new one, and checking the wrong file is worse than checking none.
	MOD_VERSION="$(sed -n 's/^mod_version=//p' "$DIR/gradle.properties" | tr -d '\r')"
	JAR="$DIR/build/libs/quickrebind-$MC-$MOD_VERSION.jar"

	if [ ! -f "$JAR" ]; then
		record "$MC jar" fail "expected $(basename "$JAR"), found: $(ls "$DIR"/build/libs/ 2>/dev/null | tr '\n' ' ')"
		continue
	fi

	if "$PYTHON" tools/check-jar.py "$JAR" --minecraft "$MC" --java "$TARGET_JAVA"; then
		record "$MC jar" ok "$(basename "$JAR")"
	else
		record "$MC jar" fail "$(basename "$JAR")"
	fi

	# ------------------------------------------------------------- self-test

	if [ "$RUN_SELFTEST" = 1 ]; then
		REPORT="$ROOT/build/verify/$MC-selftest.txt"
		# A throwaway preset folder, so a test run can never touch the presets
		# the developer actually uses.
		SANDBOX="$ROOT/build/verify/$MC-sandbox"
		rm -rf "$SANDBOX" "$REPORT"
		mkdir -p "$SANDBOX"

		SELFTEST_LOG="$ROOT/build/verify/$MC-selftest.log"
		echo "   launching the game to self-test (this takes a minute)..."

		"$GRADLEW" -p "$DIR" runClient --console=plain \
			-Dorg.gradle.java.home="$JDK" \
			-Pquickrebind.selftest="$REPORT" \
			-Pquickrebind.dir="$SANDBOX" < /dev/null > "$SELFTEST_LOG" 2>&1

		if [ -f "$REPORT" ]; then
			sed 's/^/      /' "$REPORT"

			if grep -q "RESULT: PASS" "$REPORT"; then
				record "$MC self-test" ok
			else
				record "$MC self-test" fail "$REPORT"
			fi
		else
			record "$MC self-test" fail "the game never wrote a report — $SELFTEST_LOG"
			tail -25 "$SELFTEST_LOG" | sed 's/^/      /'
		fi
	fi
done 3< "$MATRIX"

heading "summary"
printf '%b' "$SUMMARY"
printf '\n%d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
