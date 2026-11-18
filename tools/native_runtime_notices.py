"""Keep known NeoForge 26.2 platform and offline messages separate from test failures."""

import os
import re
from hashlib import sha256


COUNTER_MESSAGE = "Unable to locate English counter names in registry Perflib 009. Counters may need to be rebuilt:"
COUNTER_LOGGER = r"(?:oshi\.driver\.windows\.registry|os\.dr\.wi\.re)\.HkeyPerformanceDataUtil/"
# The same OSHI exception is present when the Windows client or server runs on its own.
COUNTER_TRACES = frozenset((
    "7C017CF1ECFC9A32F3B2EB1C9243BD425F856CC01AA022A09BAB4A2013705265",
    "3751F62B5119A5D2A05C098407BE85DAF4DB65E1B734B01F035E804EFF004C67",
))

OFFLINE_TRACES = {
    "Failed to fetch user properties": (
        r"(?:net\.minecraft\.client\.Minecraft/|minecraft/Minecraft)",
        {"46420CF918D068D5DA9A7D668BC9F81277183E11922AED6D1E0AD3B37424E7DB",
         "64B6AD5D1469F8C7D33DA73A4F0AF1B2DDE1C14BEAF85BE224E8CAB2B895BE3C"}),
    "Failed to fetch Realms feature flags": (
        r"(?:com\.mojang\.realmsclient\.client\.RealmsClient/|mojang/RealmsClient)",
        {"BDD75617F92748C2F3325FD97CCDB8034E32ACF210FA6B5E2F00BF81B760B5D1"}),
    "Couldn't connect to realms": (
        r"(?:com\.mojang\.realmsclient\.RealmsAvailability/|mojang/RealmsAvailability)",
        {"5E8D3F81BDF000068A7EB95C48C9CC06A8722559174A37F0C364B2075A998322"}),
    "Failed to retrieve profile key pair": (
        r"(?:net\.minecraft\.client\.multiplayer\.AccountProfileKeyPairManager/|minecraft/AccountProfileKeyPairManager)",
        {"8B41056B8D7FF45325AB5BD6137A2B9D29BDB33E8F4F946901F03897DE791903",
         "B3CEABE5455CEF1BD5EBEE1C1D1E571749B81C5E474FEDFBDDA77CAEDC6ABD7C"}),
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
