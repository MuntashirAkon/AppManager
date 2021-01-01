---
sidebarDepth: 2
---
# Profiles Page
Profiles page can be accessed from the options menu in the main page. It displays a list of configured profiles. Profiles can be added using the _plus_ button at the bottom-right corner, imported from the import option, created from one of the presets or even duplicated from an already existing profile. Clicking on any profile opens the [profile page][profile].

::: details Table of Contents
[[toc]]
:::

## Options Menu
There are two options menu in this page. The three dots menu at the top-right offers two options such as _presets_ and _import_.
- **Presets.** Presets option lists a number of built-in profiles that can be used as a starting point. The profiles are generated from the project [Universal Android Debloater][uad].

  _See also: [What are bloatware and how to remove them?][faq_bloatware]_
- **Import.** This option can be used to import an existing profile.

Another options menu appears when you long click on any profile. They have options such as
- **Apply now….** This option can be used to apply the profile directly. When clicked, a dialog will be displayed where you can select a [profile state][profile_state]. On selecting one of the options, the profile will be applied immediately.
- **Delete.** Clicking on delete will remove the profile immediately without any warning.
- **Duplicate.** This option can be used to duplicate the profile. When clicked, an input box will be displayed where you can set the profile name. If you click "OK", a new profile will be created and the [profile page][profile] will be loaded. The profile will not be saved until you save it manually.
- **Export.** Export the profile to an external storage. Profile exported this way can be imported using the _import_ option in the three dots menu.
- **Create shortcut.** This option can be used to create a shortcut for the profile. When clicked, there will be two options: _Simple_ and _Advanced_. The latter option allows you to set the [profile state][profile_state] before applying it while the former option use the default state that was configured when the profile was last saved.

[uad]: https://gitlab.com/W1nst0n/universal-android-debloater
[faq_bloatware]: ../faq/misc.md#what-are-bloatware-and-how-to-remove-them
[profile]: ./profile-page.md
[profile_state]: ./profile-page.md#state
