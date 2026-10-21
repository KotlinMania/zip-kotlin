"""Verify actual Zip Swift behaviors and effective compiler settings."""
import argparse
import json
import re
from pathlib import Path

CASES = {
    "swiftModuleLoads": "Zip constants and functions work from Swift",
}


def verify(text):
    text = re.sub(r"\x1b\[[0-9;]*m", "", text)
    if len(re.findall(r"Test run with 1 test[^\r\n]*passed", text)) != 1:
        raise ValueError("Expected one passing one-case Zip Swift test summary")
    if re.search(r"(?m)^.*(?:Test|Suite) .*\b(?:failed|skipped)\b", text):
        raise ValueError("Zip Swift test failed or was skipped")
    passing_cases = r"(?m)^.*Test (?:\"[^\"\r\n]+\"|[A-Za-z_]\w*\(\))(?: \(aka '[^\r\n]+'\))? passed\b"
    if len(re.findall(passing_cases, text)) != len(CASES):
        raise ValueError("Unexpected or duplicated Swift case execution")
    for name, description in CASES.items():
        label = r'(?:"' + re.escape(description) + r'"|' + re.escape(name) + r'\(\))'
        alias = r"(?: \(aka '" + re.escape(name) + r"\(\)'\))?"
        if len(re.findall(r"Test " + label + alias + r" passed\b", text)) != 1:
            raise ValueError("Expected exactly one passing execution of " + name)

    frontends = []
    manifests = 0
    for line in text.splitlines():
        if "swift-frontend" not in line or "-module-name " not in line:
            continue
        module = re.findall(r"-module-name\s+(\S+)", line)[-1]
        if module == "main" and "Package.swift" in line:
            manifests += 1
            continue
        modes = re.findall(r"-swift-version\s+(\S+)", line)
        mode = modes[-1] if modes else None
        if "-warnings-as-errors" not in line or not re.search(r"-target\s+arm64-apple-macos(?:x)?15\.0", line):
            raise ValueError("Missing Swift warning/deployment gate: " + module)
        allowed = ["6", "6.0"] if module == "SwiftTestHarnessTests" else ["5", "5.0"]
        if module == "SwiftTestHarnessPackageTests":
            allowed = [None, "6", "6.0"]
        if mode not in allowed:
            raise ValueError("Unexpected effective Swift language mode: " + module)
        frontends.append({"module": module, "swift_language_mode": mode,
                          "warnings_as_errors": True, "macos_deployment_target": "15.0"})
    required = {"Zip", "KotlinRuntimeSupport", "KotlinCoroutineSupport", "SwiftTestHarnessTests"}
    if not required <= {row["module"] for row in frontends}:
        raise ValueError("Missing actual dependency/consumer frontend evidence")
    return {"actual_behavior_tests": len(CASES), "failures": 0, "skips": 0,
            "frontends": frontends, "configuration_frontends": manifests,
            "generated_runner_classified_separately": True}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = verify(args.log.read_text())
    output = args.output or args.log.with_suffix(".json")
    output.write_text(json.dumps(result, indent=2) + "\n")
    print("Verified one actual Zip Swift behaviors and effective compiler settings")
