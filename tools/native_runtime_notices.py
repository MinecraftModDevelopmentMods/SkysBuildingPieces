"""Keep known NeoForge 26.1.2 platform and offline messages separate from test failures."""

import os
import re
from hashlib import sha256


COUNTER_MESSAGE = "Unable to locate English counter names in registry Perflib 009. Counters may need to be rebuilt:"
COUNTER_LOGGER = r"(?:oshi\.driver\.windows\.registry|os\.dr\.wi\.re)\.HkeyPerformanceDataUtil/"
# No Windows counter trace is allowed until reproduced on this target.
COUNTER_TRACES = frozenset()

OFFLINE_TRACES = {
    "Couldn't connect to realms": (
        '(?:com\\.mojang\\.realmsclient\\.RealmsAvailability/|mojang/RealmsAvailability)',
        {'2C3DFD8FE708F74AB95BEBB4CD8AC63D870D0BDCBCF1F1F076CF08F3949C50F8'}),
    'Failed to fetch Realms feature flags': (
        '(?:com\\.mojang\\.realmsclient\\.client\\.RealmsClient/|mojang/RealmsClient)',
        {'137189539B1FB99FD52EC28A35D7C5959098AC860190BE5E7C2F0B41714988CC'}),
    'Failed to fetch user properties': (
        '(?:net\\.minecraft\\.client\\.Minecraft/|minecraft/Minecraft)',
        {'96C12849119AB52B3D8E8A3BBE4136F34BD4E960BF1D234F13649747C8E8A0C5'}),
}

OFFLINE_THREADS = {
    "Couldn't connect to realms": 'IO-Worker-\\d+',
    'Failed to fetch Realms feature flags': 'Download-\\d+',
    'Failed to fetch user properties': 'Download-\\d+',
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
    """Accept only the exact token-0 traces from the native target control."""
    notices = []
    for message, row in OFFLINE_TRACES.items():
        text, found = remove_known_traces(text, OFFLINE_THREADS[message], {message: row})
        notices.extend(found)
    return text, sorted(notices)
