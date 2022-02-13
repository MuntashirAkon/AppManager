<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
## Usage

```
./doctool.sh VERB [ARGS]
Where:
check       Run dependency checker.
checkabuse <target-dir>         Detect spams or mistranslations.
```

## Requirements

* [pandoc](https://github.com/jgm/pandoc) (v2.13 or later) (for macOS, run `brew install pandoc`)
* [pandoc-crossref](https://github.com/lierdakil/pandoc-crossref) (for macOS, run `brew install pandoc-crossref`)
* Bash
* GNU grep (for macOS, run `brew install ggrep`)
* GNU Sed (for macOS, run `brew install gsed`)
* minify (for macOS, run `brew install minify`)
* urlextract (`pip install urlextract`)
* find

### Adding a new article

1. Create a new article following the typical Latex syntax.
2. Add custom translation keys for the title tags e.g. `section{}`,
   `subsection{}`, `subsubsection{}`, `chapter{}`, `caption{}` and
   `paragraph{}` at the end of the title line.

   **Key format:** `%%##$key>>` where `key` is an arbitrary key for the title
   unique to the file and must match this regex: `[a-zA-Z0-9-_\.]+`.

   **Example:** Consider the following line in Latex in a file located at 
   `appendices/app-ops.tex`:
   ```latex
   \chapter{App Ops}\label{ch:app-ops}
   ```

   This has to be altered as follows:
   ```latex
   \chapter{App Ops}\label{ch:app-ops} %%##$chapter-title>>
   ```
   
   On issuing an `rebase`, the line will be extracted as follows in strings.xml:
   ```xml
   <string name="appendices$app-ops$$chapter-title">App Ops</string>
   ```
   
   Finally, the full title must be within a single line.

3. Add custom translation keys for the contents.

   **Format:** Start of the content is denoted by `%%!!key<<` where key is an
   arbitrary key for the content unique to the file and must match this regex:
   `[a-zA-Z0-9-_\.]+`. End of the content is denoted by `%%!!>>`.

   **Example:** Consider the following paragraph in Latex:
   ```latex
   After \hyperref[subsubsec:location-of-developer-options]{locating the developer options}, enable \textbf{Developer option} (if not already).
   After that, scroll down a bit until you will find the option \textbf{USB debugging}.
   Use the toggle button on the right-hand side to enable it.
   At this point, you may get an alert prompt where you may have to click \textit{OK} to actually enable it.
   You may also have to enable some other options depending on device vendor and ROM.
   ```

   This has to be altered as follows:
   ```latex
   %%!!enable-usb-debug<<
   After \hyperref[subsubsec:location-of-developer-options]{locating the developer options}, enable \textbf{Developer option} (if not already). After that, scroll down a bit until you will find the option \textbf{USB debugging}.
   Use the toggle button on the right-hand side to enable it.
   At this point, you may get an alert prompt where you may have to click \textit{OK} to actually enable it.
   You may also have to enable some other options depending on device vendor and ROM\@.
   %%!!>>
   ```

   On issuing an `rebase`, strings within `%%!!enable-usb-debug<<` and `%%!!>>`
   will be extracted under the key `guide$aot$enable-usb-debug`.

   Note that the start and end markers must be located in a separate line with
   no additional spaces, similar to EOF markers.
