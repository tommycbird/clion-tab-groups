# Tab Groups

A JetBrains IDE plugin (built for CLion) that organizes your open editor files into
named, colored, collapsible groups driven by regex.

Designed for a workflow where the native editor tabs are hidden and this tool window
acts as your tab bar instead.

## Features

- **Regex groups** — each group has a name, a color, and a regex.
- **Priority ordering** — a numeric priority per group. Lowest number is tested first
  (first match wins) and is also the display order. Ties fall back to list position.
- **Misc catch-all** — anything that matches no group lands in a customizable **Misc**
  group, which is always shown last.
- **Collapsible, colored headers** — each group is a collapsible section with a color
  swatch and file count.
- **Follows the editor** — selecting/focusing a file in the editor highlights it in the
  tool window. If its group is collapsed it stays collapsed, and the active file is shown
  inline on the collapsed header so you never lose track of it.
- **Single-click to open** — click a file entry to open/focus it.
- **Name or full-path matching** — match regexes against the file name (default) or the
  full path.

## Usage

1. Open the **Tab Groups** tool window (left dock by default).
2. Configure groups in **Settings → Tools → Tab Groups**:
   - Add/remove groups, set **Name**, **Priority**, **Regex**, and **Color**.
   - Set the **Misc** group name/color.
   - Toggle **match against full path** if you want directory-based grouping.

Regexes use `find` semantics (partial match), so anchor with `^`/`$` as needed. Add
`(?i)` for case-insensitivity. Examples:

- `(?i)^test_|Test\.cpp$` — files starting with `test_` or ending in `Test.cpp`
- `\.(h|hpp|hxx)$` — C/C++ headers
- `(?i)^(?!test_).*\.py$` — Python files that aren't tests

## Build

Requires JDK 17.

```bash
./gradlew buildPlugin   # -> build/distributions/clion-tab-groups-<version>.zip
./gradlew runIde        # try it in a sandbox IDE
```

## Install

**Settings → Plugins → ⚙ → Install Plugin from Disk…** and pick the built zip, then
restart the IDE.

Built against the IntelliJ Platform (IDEA Community) using only generic platform APIs,
so it runs in CLion and other JetBrains IDEs.

## License

MIT
