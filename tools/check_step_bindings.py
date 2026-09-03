"""Static check that every step in a feature file has a matching step definition.

Usage: python tools/check_step_bindings.py <feature file> <steps java file>
Catches step-text renames that would otherwise only surface as undefined steps at run time.
"""
import re
import sys

DEF_RE = re.compile(r'@(?:Given|When|Then)\("(.+?)"\)')
STEP_RE = re.compile(r'\s*(?:Given|When|Then|And|But)\s+(.*\S)\s*$')


def main(feature_path: str, steps_path: str) -> int:
    with open(feature_path, encoding="utf-8") as fh:
        feature_lines = fh.read().splitlines()
    with open(steps_path, encoding="utf-8") as fh:
        definitions = set(DEF_RE.findall(fh.read()))

    used = []
    for line in feature_lines:
        match = STEP_RE.match(line)
        if match and match.group(1) not in used:
            used.append(match.group(1))

    unbound = [text for text in used if text not in definitions]
    unused = sorted(text for text in definitions if text not in used)

    print(f"feature steps: {len(used)}  definitions: {len(definitions)}")
    print(f"UNBOUND: {len(unbound)}")
    for text in unbound:
        print(f"  - {text}")
    print(f"definitions not used by this feature: {len(unused)}")
    for text in unused:
        print(f"  - {text}")
    return 1 if unbound else 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        raise SystemExit(2)
    raise SystemExit(main(sys.argv[1], sys.argv[2]))
