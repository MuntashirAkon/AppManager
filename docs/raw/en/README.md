<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
## Usage

```
./doctool.sh VERB [ARGS]
Where:
update <output.xml>     Extract strings and create xliff translation file.
merge <input.xml> <output-dir>  Merge translation from xliff to TeX.
check                   Run dependency checker.
checkabuse <base-dir> <target-dir>  Detect spams or mistranslations.
```

## Requirements

* [pandoc](https://github.com/jgm/pandoc) (v2.13 or later) (for macOS, run `brew install pandoc`)
* [pandoc-crossref](https://github.com/lierdakil/pandoc-crossref) (for macOS, run `brew install pandoc-crossref`)
* Python
* Bash
* GNU awk (for macOS, run `brew install gawk`)
* GNU grep (for macOS, run `brew install ggrep`)
* GNU Sed (for macOS, run `brew install gsed`)
* xmllint
* Perl
* urlextract (`pip install urlextract`)
* find
* rsync

## Manual

- `./doctool.sh update`: Extracts strings from TeX and create `strings.xml`
  which can be copied (with identical name) for translation. All line breaks are ignored. Attention should be given
  while translating Latex tags e.g.,
  `\hyperref[]`, `\textbf`, `\cref`.
- `./doctool.sh merge`: Updates TeX from `strings.xml`. If the syntax of
  `strings.xml` is invalid, errors will be thrown. Depending on the processing speed, it may take several minutes.

### Adding a new article

1. Create a new article following the typical Latex syntax.
2. Add custom translation keys for the title tags e.g. `section{}`,
   `subsection{}`, `subsubsection{}`, `chapter{}`, `caption{}` and
   `paragraph{}` at the end of the title line.

   Key format: `%%##string_key==title>>` where `title` is a keyword.

   Example: Consider the following line in Latex:
   ```latex
   \chapter{App Ops}\label{ch:app-ops}
   ```

   This has to be altered as follows:
   ```latex
   \chapter{App Ops}\label{ch:app-ops} %%##appendices_appops-chapter==title>>
   ```

   On issuing an `update`, the line will be extracted as follows in xliff:
   ```xml
   <string name="appendices_appops-chapter==title">App Ops</string>
   ```

3. Add custom translation keys for the contents.

   Example: Consider the following paragraph in Latex:
   ```latex
   After \hyperref[subsubsec:location-of-developer-options]{locating the developer options}, enable \textbf{Developer option} (if not already).
   After that, scroll down a bit until you will find the option \textbf{USB debugging}.
   Use the toggle button on the right-hand side to enable it.
   At this point, you may get an alert prompt where you may have to click \textit{OK} to actually enable it.
   You may also have to enable some other options depending on device vendor and ROM.
   ```

   This has to be altered as follows:
   ```latex
   %%!!guide_aot-enableusbdbg<<
   After \hyperref[subsubsec:location-of-developer-options]{locating the developer options}, enable \textbf{Developer option} (if not already). After that, scroll down a bit until you will find the option \textbf{USB debugging}.
   Use the toggle button on the right-hand side to enable it.
   At this point, you may get an alert prompt where you may have to click \textit{OK} to actually enable it.
   You may also have to enable some other options depending on device vendor and ROM.
   %%!!>>
   ```

   On issuing an `update`, strings within `%%!!string_key<<` and `%%!!>>` will be extracte under the
   key `guide_aot-enableusbdbg`.
