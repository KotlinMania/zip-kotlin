"""Validate the unmodified producer manifest; never rewrite generated APIs."""
import re
import sys
from pathlib import Path
text = Path(sys.argv[1]).read_text()
if not re.match(r"// swift-tools-version:\s*5\.9(?:\s|$)", text):
    raise ValueError("Expected the published producer's Swift tools-version 5.9")
if re.search(r"swiftLanguage(?:Modes|Versions)\s*:|swiftLanguageMode\s*\(", text):
    raise ValueError("Unexpected generated language-mode override")
print("Unmodified producer manifest: dependency Swift 5; authored harness remains Swift 6")
