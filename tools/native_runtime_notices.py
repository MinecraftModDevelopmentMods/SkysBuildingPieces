"""Keep known NeoForge 26.3 platform and offline messages separate from test failures."""

import os
import re
from hashlib import sha256


COUNTER_MESSAGE = "Unable to locate English counter names in registry Perflib 009. Counters may need to be rebuilt:"
COUNTER_LOGGER = r"(?:oshi\.driver\.windows\.registry|os\.dr\.wi\.re)\.HkeyPerformanceDataUtil/"
# The same OSHI exception is present when the Windows client or server runs on its own.
COUNTER_TRACES = frozenset((
    "765AD01973F5FE5829DE0B78DC2714CB85A8B26AD4506BD9F8984DADC19DC8A3",
    "196392CF9E83653E4434DDA811736464785DF5211DF0E1294A9E29E1DE5D2745",
))

OFFLINE_TRACES = {
    "Failed to fetch user properties": (
        r"(?:net\.minecraft\.client\.Minecraft/|minecraft/Minecraft)",
        {"ADAF5E4F2459F568946216356024CEB73F0D195D0DD4FBD9B741BF42A17EABEF",
         "7091256E4532D2A71B94BAA58E684B64906E9FF815CE678BA9D51E5EB71C57CC"}),
    "Failed to fetch Realms feature flags": (
        r"(?:com\.mojang\.realmsclient\.client\.RealmsClient/|mojang/RealmsClient)",
        {"7C8D23CDB61777F30D6F67A0C5EB51A1F298AF61971161B0DD32459F40E726E9"}),
    "Couldn't connect to realms": (
        r"(?:com\.mojang\.realmsclient\.RealmsAvailability/|mojang/RealmsAvailability)",
        {"188E2A54FEE04A3B8E6E3D4EBD3614FB5BFE1B7F3746D551A4255F49B3F9E5FC"}),
}


def remove_known_traces(text, thread, rows):
    lines = text.splitlines(keepends=True)
    keep = [True] * len(lines)
    seen = set()
    for index, line in enumerate(lines):
        for message, (logger, digests) in rows.items():
            header = rf"\[[^\r\n]+\] \[{thread}/ERROR\](?: \[{logger}\])?: "
            if not re.fullmatch(header + re.escape(message), line.rstrip()):
                continue
            end = index + 2
            while end < len(lines) and lines[end].strip().startswith(("at ", "Caused by:", "Suppressed:", "... ")):
                end += 1
            frames = [entry.strip() for entry in lines[index + 1:end]]
            # Log4j adds jar labels to stack frames in some layouts.
            frames = [re.sub(r" ~?\[[^\r\n]+\](?: \{\})?$", "", entry)
                      if entry.startswith("at ") else entry for entry in frames]
            digest = sha256("\n".join(frames).encode()).hexdigest().upper()
            if digest not in digests:
                continue
            if message in seen:
                raise ValueError("Repeated startup diagnostic: " + message)
            seen.add(message)
            keep[index:end] = [False] * (end - index)
    return "".join(line for line, retain in zip(lines, keep) if retain), sorted(seen)


def windows_counter_notice(text):
    if os.name != "nt":
        return text
    return remove_known_traces(text, "main",
                               {COUNTER_MESSAGE: (COUNTER_LOGGER, COUNTER_TRACES)})[0]


def offline_client_notices(text):
    """The disposable client uses token 0. Require the complete matching trace."""
    return remove_known_traces(text, r"Download-\d+", OFFLINE_TRACES)
