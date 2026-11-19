# Contributing

Keep the code easy to read and straightforward to review.

- Use four spaces, UTF-8 and LF line endings. Aim for 120 columns; do not split a registry ID or other literal just to meet the limit.
- Put each statement on its own line and use braces for conditional and loop bodies, including early returns.
- Leave a blank line between logical steps. Keep related assignments together rather than separating every line.
- Name variables for their role. Use `inventorySlot`, `cellX` or `chunkX` when that context matters, rather than a single letter or a type abbreviation. Keep established names such as `world` and `state` when their meaning is already clear.
- Declare separate local values on separate lines. Explain packed masks and coordinate conventions where the names cannot tell the whole story.
- Write comments in plain English. Explain a decision, save-format constraint or surprising API behaviour instead of repeating what the next line says.
- Document public extension points, including when they can be called and what they preserve. Keep comments current when behaviour changes.

For Eclipse, import [the Java formatter profile](style/eclipse-java-formatter.xml) under **Java > Code Style > Formatter** and select **Sky Java**. The formatter handles layout; braces and logical grouping still need a quick human review.

Treat registry IDs, metadata palettes, configuration keys and saved NBT fields as compatibility contracts. A readability change should not alter them. Keep behavioural changes separate and run the relevant tests before submitting a pull request. See [the README](README.md) for this branch's toolchain and checks.
