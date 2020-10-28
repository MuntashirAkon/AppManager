---
sidebarDepth: 2
tags:
  - exodus
  - tracker
  - ad
  - blocking
  - library
  - anti-feature
---
# Scanner Page
**Scanner page** appears after clicking on the _scanner_ button in the [App Info tab][app_info]. External APK files can also be opened for scanning from file managers, web browsers, etc.

It scans for trackers & libraries and display the number of trackers & libraries as a summary. It also displays checksums of the apk file as well as the signing certificate(s).

Clicking on the first item (i.e. number of classes) opens a new page containing a list of tracker classes for the app. All classes can also be viewed by clicking on the _Toggle Class Listing_ menu. A sneak-peek of each class can be viewed by simply clicking on any class item.

::: warning Notice
Due to various limitations, it is not possible to scan all the components of an apk file. This is especially true if an apk is highly obfuscated. The scanner also does not check strings (or website signatures).
:::

The second item lists the number of trackers along with their names. Clicking on the item displays a dialog containing the name of trackers, matched signatures and the number of classes against each signature. The tracker names may have some prefixes such as:
- `°` denotes that the tracker is missing in the εxodus' list (taken from [IzzyOnDroid repo][izzy])
- `²` denotes that the tracker is in the [ETIP][etip] stand-by list i.e. whether it is an actual tracker is not yet decided
- `µ` denotes micro non-intrusive tracker meaning that these trackers are harmless but still a tracker
- `?` denotes that the tracker status is unknown

The third item lists the number of libraries along with their names. These information are taken from [IzzyOnDroid repo][izzy].

_See also: [FAQ: Tracker vs tracker components][t_vs_tc]_

[app_info]: ./app-details-page.md#app-info-tab
[etip]: https://etip.exodus-privacy.eu.org
[t_vs_tc]: ../faq/app-components.md#tracker-classes-versus-tracker-components
[izzy]: https://gitlab.com/IzzyOnDroid/repo
