## Usage

`./doctool.sh COMMAND ARGS`

buildhtml
Build HTML from TeX

updatetranslation
Extract strings and create xliff translation file

mergetranslation
Merge translation from xliff to TeX

help
Show this help and exit

checkdeps
Run dependency checker



## Dependencies

・pandoc
Install pandoc above v2.13.
Download releases from here and install it.(maybe your package manager have pandoc package,but it will cause bug due to it is outdated)
https://github.com/jgm/pandoc/releases/tag/2.13

・pandoc-crossref
#### Solution1
Download file from here
https://github.com/lierdakil/pandoc-crossref/releases
Extract it
Put files to working directory(THIS DIRECTORY)

#### Solution2
Follow this guide to install pandoc-crossref in to system
https://github.com/lierdakil/pandoc-crossref/blob/master/README.md

・Python
Run "apt install python"

・GNU Sed
Most distros installed by default.Probably you have to install yourself if you're using mac.
If not, run "apt install sed"

・Bash
All distros installed by default.Don't use "sh".Please use "bash"

・Awk
Most distros installed by default.
If not,run "apt install awk"

・GNU grep
Most distros installed by default.
If not,run "apt install grep"
Probably you have to install it manually too if you are using mac

・xmllint
Probably installed by default in your distro.
If not,run "apt install xmllint"

・Perl
Run "apt install perl"



## Instruction

`./doctool.sh updatetranslation`
This command will extract strings from TeX and create strings.xml.
Copy it with identificatable name and translate it.
Please note that line breaks are ignored.
And Please don't break TeX tags(eg,\hyperref[],\textbf,\cref)

`./doctool.sh mergetranslation`
Then,this command will update TeX from strings.xml.
If strings.xml syntax is invaild,you will get error when you run this command.
It takes several times.

`./doctool.sh buildhtml`
Finally this command will build html file.
Please check output file to check if your translation applied correctly

## How to add new article to base docs and make it translatable

1.Add article by following latex syntax

2.Add string keys for titles
Title means strings written in `section{},subsection{},subsubsection{},chapter{},caption{},paragraph{}` tex tags.
Add string keys like %%##string_key==title>> at the end of title line.
For example,
If you added line `\chapter{App Ops}\label{ch:app-ops}`,change it to like `\chapter{App Ops}\label{ch:app-ops} %%##appendices_appops-chapter==title>>`.
This will extracted as `<string name="appendices_appops-chapter==title">App Ops</string>` to Xliff.
Make sure that title string key names must be finished by `==title`, otherwise you will get bugs when merging translation.

3.Add string keys for content
If you added lines like bellow:
``
After \hyperref[subsubsec:location-of-developer-options]{locating the developer options}, enable \textbf{Developer
option} (if not already). After that, scroll down a bit until you will find the option \textbf{USB debugging}. Use the
toggle button on the right hand side to enable it. At this point, you may get an alert prompt where you may have to
click \textit{OK} to actually enable it. You may also have to enable some other options depending on device vendor and
ROM. Here are some examples:
``
Change it like this:
``
%%!!guide_aot-enableusbdbg<<
After \hyperref[subsubsec:location-of-developer-options]{locating the developer options}, enable \textbf{Developer
option} (if not already). After that, scroll down a bit until you will find the option \textbf{USB debugging}. Use the
toggle button on the right hand side to enable it. At this point, you may get an alert prompt where you may have to
click \textit{OK} to actually enable it. You may also have to enable some other options depending on device vendor and
ROM. Here are some examples:
%%!!>>
``
This will allow extracting enclosed strings within `%%!!string_key<<` and `%%!!>>`
