#!/usr/bin/env python3
"""Checks a built QuickRebind jar without launching anything.

A jar that compiles is not a jar that works. The failures this catches are the
ones that only show up on somebody else's machine: a version placeholder that
never got substituted, an entrypoint naming a class that isn't in the jar, a
`java` dependency that disagrees with what the classes were actually compiled
to, or a build that quietly stopped including core.

Usage:  python tools/check-jar.py <jar> --minecraft 1.21.1 [--java 21]
"""
import argparse
import json
import re
import struct
import sys
import zipfile

# Class file major version -> the Java release that produces it.
CLASS_FILE_VERSIONS = {52: 8, 53: 9, 55: 11, 61: 17, 62: 18, 63: 19, 64: 20, 65: 21,
                       66: 22, 67: 23, 68: 24, 69: 25, 70: 26}

REQUIRED_ENTRIES = [
    "fabric.mod.json",
    "assets/quickrebind/lang/en_us.json",
]

# The shared half. If a build stops pulling core in, these vanish and the mod
# fails at runtime rather than at compile time.
REQUIRED_CORE_CLASSES = [
    "com/bogdan/quickrebind/core/ApplyEngine.class",
    "com/bogdan/quickrebind/core/PresetStore.class",
    "com/bogdan/quickrebind/core/ShareCode.class",
    "com/bogdan/quickrebind/core/SharedPaths.class",
]


class Checker:
    def __init__(self, jar_path):
        self.jar_path = jar_path
        self.failures = []
        self.notes = []

    def fail(self, message):
        self.failures.append(message)

    def note(self, message):
        self.notes.append(message)

    def run(self, minecraft, java_release):
        try:
            jar = zipfile.ZipFile(self.jar_path)
        except (OSError, zipfile.BadZipFile) as e:
            self.fail(f"cannot open the jar: {e}")
            return

        with jar:
            names = set(jar.namelist())

            for entry in REQUIRED_ENTRIES:
                if entry not in names:
                    self.fail(f"missing {entry}")

            for entry in REQUIRED_CORE_CLASSES:
                if entry not in names:
                    self.fail(f"core is not in the jar: missing {entry}")

            if "fabric.mod.json" not in names:
                return

            metadata = self.check_metadata(jar, minecraft)
            self.check_entrypoints(jar, names, metadata)
            self.check_class_files(jar, names, metadata, java_release)
            self.check_lang(jar)

    # ------------------------------------------------------------- metadata

    def check_metadata(self, jar, minecraft):
        try:
            metadata = json.loads(jar.read("fabric.mod.json").decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            self.fail(f"fabric.mod.json is not valid JSON: {e}")
            return {}

        if metadata.get("id") != "quickrebind":
            self.fail(f"mod id is {metadata.get('id')!r}, expected 'quickrebind'")

        version = str(metadata.get("version", ""))

        if "${" in version or not version:
            self.fail(f"the version placeholder was not substituted: {version!r}")
        else:
            self.note(f"version {version}")

        if metadata.get("environment") != "client":
            self.fail(f"environment is {metadata.get('environment')!r}, expected 'client' "
                      "— a client-only mod that claims '*' gets loaded on servers")

        depends = metadata.get("depends", {})
        declared = str(depends.get("minecraft", ""))

        if not declared:
            self.fail("depends.minecraft is missing")
        elif not self.version_matches(declared, minecraft):
            self.fail(f"depends.minecraft is {declared!r} but this jar was built "
                      f"against Minecraft {minecraft}")
        else:
            self.note(f"declares minecraft {declared}")

        if "fabric-api" not in depends:
            self.fail("depends is missing fabric-api, which the entrypoint uses")

        return metadata

    @staticmethod
    def version_matches(declared, minecraft):
        """Whether a fabric.mod.json range plausibly covers the version we built.

        Deliberately shallow — this is a wiring check, not a semver engine. It
        only has to catch the copy-paste failure where 1.20.1's jar is left
        declaring `~1.21.1`.

        Compares whole version numbers rather than substrings, because "1.21.1"
        is a substring of "1.21.11" and those are two different builds we ship.
        """
        bounds = re.findall(r"\d+(?:\.\d+)*", declared)

        if minecraft in bounds:
            return True

        # Only a genuine range gets the benefit of the doubt. A pin such as
        # `~1.21.11` names one version and either matched above or is wrong —
        # accepting it because it shares a family with 1.21.1 is how a
        # mislabelled jar reaches Modrinth.
        if not re.search(r"[<>]", declared):
            return False

        # ">=1.21.9 <1.21.12": accept when every bound shares the major.minor of
        # what we built, so the range is at least in the right family. Anything
        # wider than that is a claim worth making deliberately.
        family = ".".join(minecraft.split(".")[:2])
        return bool(bounds) and all(bound.startswith(family + ".") or bound == family
                                    for bound in bounds)

    # ---------------------------------------------------------- entrypoints

    def check_entrypoints(self, jar, names, metadata):
        entrypoints = metadata.get("entrypoints", {})

        if not entrypoints.get("client"):
            self.fail("no client entrypoint declared")
            return

        for kind, entries in entrypoints.items():
            for entry in entries:
                class_name = entry if isinstance(entry, str) else entry.get("value", "")
                path = class_name.replace(".", "/") + ".class"

                if path not in names:
                    self.fail(f"{kind} entrypoint {class_name} is not in the jar — "
                              "the game would crash on load")
                else:
                    self.note(f"{kind} entrypoint {class_name} present")

    # ---------------------------------------------------------- class files

    def check_class_files(self, jar, names, metadata, java_release):
        classes = [n for n in names if n.endswith(".class")]

        if not classes:
            self.fail("no classes in the jar at all")
            return

        majors = set()

        for name in classes:
            header = jar.read(name)[:8]

            if len(header) < 8 or header[:4] != b"\xca\xfe\xba\xbe":
                self.fail(f"{name} is not a class file")
                continue

            majors.add(struct.unpack(">H", header[6:8])[0])

        releases = sorted(CLASS_FILE_VERSIONS.get(m, m) for m in majors)
        highest = max(releases)
        self.note(f"{len(classes)} classes, compiled for Java {releases}")

        if java_release is not None and highest != java_release:
            self.fail(f"classes are compiled for Java {highest}, expected {java_release}")

        # The number people actually trip over: a jar whose classes need a newer
        # Java than its own metadata admits refuses to load with a confusing
        # message, and only for the players on the older runtime.
        declared = str(metadata.get("depends", {}).get("java", ""))
        floor = re.search(r"(\d+)", declared)

        if floor and highest > int(floor.group(1)):
            self.fail(f"depends.java is {declared!r} but the classes need Java {highest}")

    # ----------------------------------------------------------------- lang

    def check_lang(self, jar):
        try:
            lang = json.loads(jar.read("assets/quickrebind/lang/en_us.json").decode("utf-8"))
        except KeyError:
            return
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            self.fail(f"en_us.json is not valid JSON: {e}")
            return

        missing = [key for key in ("key.quickrebind.open", "key.quickrebind.cycle")
                   if key not in lang]

        if missing:
            self.fail(f"en_us.json is missing {', '.join(missing)} — the keybind would "
                      "show as a raw translation key in the controls screen")
        else:
            self.note(f"{len(lang)} translation keys")


def main():
    # The Windows console defaults to a legacy code page, which turns the dashes
    # in these messages into mojibake right where someone is trying to read why
    # their build failed.
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):
        pass

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar")
    parser.add_argument("--minecraft", required=True,
                        help="the Minecraft version this jar was built against")
    parser.add_argument("--java", type=int, default=None,
                        help="the Java release the classes should be compiled for")
    args = parser.parse_args()

    checker = Checker(args.jar)
    checker.run(args.minecraft, args.java)

    for note in checker.notes:
        print(f"      {note}")

    for failure in checker.failures:
        print(f"      FAIL  {failure}")

    return 1 if checker.failures else 0


if __name__ == "__main__":
    sys.exit(main())
