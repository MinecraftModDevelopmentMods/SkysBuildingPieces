"""Keep the known Forge 66 Windows system report out of test failure checks."""

import hashlib
import os
from pathlib import Path
import re
import sys


PERFLIB = re.compile(
    r"\[[^\r\n]+\] \[main/ERROR\] "
    r"\[(?:oshi\.driver\.windows\.registry|os\.dr\.wi\.re)\.HkeyPerformanceDataUtil/\]: "
    r"Unable to locate English counter names in registry Perflib 009\. Counters may need to be rebuilt: ?"
)
COMPACT_PERFLIB = re.compile(
    r"\[[^\r\n]+\] \[main/ERROR\]: "
    r"Unable to locate English counter names in registry Perflib 009\. Counters may need to be rebuilt: ?"
)
# The packaged logger omits both the logger name and the frame suffixes.
# Require the entire call stack from the Forge-only control in that format.
COMPACT_TRACES = {
    "9bb8e40d2b5de478652e405d006e58f3c62f50653e7c4a279ec8bb454eb90bdc",  # Dedicated server
    "7f3aaf1aff8842b81b11164979b6de11140d3558d999d579d59c267aa15a92b7",  # Client
}

JNA_EXCEPTION = "com.sun.jna.platform.win32.Win32Exception: The parameter is incorrect."


def forge_windows_log(text, *, enabled):
    """Remove one proven diagnostic from the audit view, leaving the log file alone."""
    if not enabled:
        return text

    lines = text.splitlines(keepends=True)
    matches = [i for i, line in enumerate(lines) if (PERFLIB.fullmatch(line.rstrip("\r\n"))
                   or COMPACT_PERFLIB.fullmatch(line.rstrip("\r\n")))]
    if len(matches) != 1:
        return text

    start = matches[0]
    if start + 4 >= len(lines) or lines[start + 1].rstrip("\r\n") != JNA_EXCEPTION:
        return text

    end = start + 2
    while end < len(lines) and lines[end].lstrip().startswith("at "):
        end += 1
    if COMPACT_PERFLIB.fullmatch(lines[start].rstrip("\r\n")):
        trace = "\n".join(line.rstrip("\r\n") for line in lines[start + 1:end])
        if hashlib.sha256(trace.encode("utf-8")).hexdigest() not in COMPACT_TRACES:
            return text

    stack = "".join(lines[start + 2:end])
    if not all(frame in stack for frame in (
        "com.sun.jna.platform@5.17.0/com.sun.jna.platform.win32.Advapi32Util.registryGetStringArray",
        "com.github.oshi@6.9.0/oshi.driver.windows.registry.HkeyPerformanceDataUtil.mapCounterIndicesFromRegistry",
        "minecraft@26.3/net.minecraft.SystemReport.",
        "net.minecraftforge.fmlloader@26.3-66.0.2/",
    )):
        return text

    return "".join(lines[:start] + lines[start + 2:])


if __name__ == "__main__":
    text = Path(sys.argv[1]).read_text(encoding="utf-8")
    sys.stdout.write(forge_windows_log(text, enabled=os.name == "nt"))
