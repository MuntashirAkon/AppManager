<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

<p align="center">
  <img src="docs/raw/images/icon.png" alt="App Manager Logo" height="150">
</p>

<h1 align="center">App Manager</h1>

<p align=center>
  <a href="https://muntashirakon.github.io/AppManager">Docs</a> ·
  <a href="https://github.com/MuntashirAkon/AppManager/releases">Releases</a> ·
  <a href="https://t.me/AppManagerChannel">Telegram Channel</a>
</p>

---

## Features

### General features
- Fully reproducible, copylefted libre software (GPLv3+)
- Material design (but not material colours!)
- No unnecessary permissions
- [Does not connect to the Internet](https://muntashirakon.github.io/AppManager/en/#x1-1780004.3.2)
- Displays as much info as possible in the main page
- Lists activities, broadcast receivers, services, providers, app ops, permissions, signatures, shared libraries, etc.
  of any app
- Launch (exportable) activities and services
- Create (customizable) shortcuts of activities
- [Intercept activities](https://muntashirakon.github.io/AppManager/en/#x1-1220002.8)
- Scan for trackers and libraries in apps and list (all or only) tracking classes (and their code dump)
- View the manifest of an app
- Display app usage, data usage (mobile and wifi), and app storage info (requires “Usage Access” permission)
- Install/uninstall APK files (including APKS, APKM and XAPK with OBB files)
- Share APK files
- Back up/restore APK files
- Batch operations
- One-click operations
- Logcat viewer
- [Profiles](https://muntashirakon.github.io/AppManager/en/#x1-710002.5) (including presets for quick debloating)
- Open app in Aurora Store or in your favourite F-Droid client
- Sign APK files with custom signatures before installing
- Backup encryption: OpenPGP via OpenKeychain, RSA (hybrid encryption with AES) and AES.

### Root/ADB-only features

- Revoke runtime (AKA dangerous) and development permissions
- Change mode of any app op
- Display/kill/force-stop running apps or processes
- Clear app data or app cache
- View/change net policy
- Control battery optimization

### Root-only features

- Block any activities, broadcast receivers, services, or providers of an app with native import/export as well as Watt
  and Blocker import support
- View/edit/delete shared preferences of any app
- Back up/restore apps with data, rules and extras (such as permissions, battery optimization, SSAID, etc.)
- View system configurations including blacklisted or whitelisted apps, permissions, etc.
- View/change SSAID

…and many more! This single app combines the features of 5 or 6 apps any tech-savvy person needs!

### Upcoming features
- APK editing
- Routine operations
- Backup encryption: Elliptive-curve cryptography (ECC)
- Finder: Find app components, permissions etc. in all apps
- Enable/disable app actions such as launch on boot
- Panic responder for Ripple
- Crash monitor
- Systemless disable/uninstall of system apps
- Import/export app list
- Terminal emulator
- Database viewer and editor, etc.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
height="80" />](https://f-droid.org/packages/io.github.muntashirakon.AppManager)

## Translations

Help translate [the app strings](https://hosted.weblate.org/engage/app-manager/) and [the docs](https://hosted.weblate.org/projects/app-manager/docs/) at Hosted Weblate.


[![Translation status](https://hosted.weblate.org/widgets/app-manager/-/multi-auto.svg)](https://hosted.weblate.org/engage/app-manager/)


## Mirrors

[GitLab](https://gitlab.com/muntashir/AppManager) · [Riseup](https://0xacab.org/muntashir/AppManager) · [Codeberg](https://codeberg.org/muntashir/AppManager)

## Screenshots

### Dark

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/14.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" height="500dp" />

### Light

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/13.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" height="500dp" />

## Build Instructions
See [BUILDING.rst](BUILDING.rst)

## Contributing

See [CONTRIBUTING.rst](CONTRIBUTING.rst)

## Donation and Funding

**App Manager doesn't support any donations directly.** However, if you like my projects (App Manager being one of them)
, you can buy me a coffee by sending an anonymous donation to one of the following **Bitcoin** addresses:

```
33TDkWVv5EgwfKGJk7YaS2Ev1CBzBP9Sav
38bzvWDD99dJhXg9tC4yQEnGdnAKPtwSXG
3FHTxPoYa92dNJK6pkhwyVkMG8Vv3VpGpg
```

Or, in the following **Ethereum** address:

```
0xa048a882301d9503d8c27Da8044c4E72dF14C817
```

By sending me BTC/ETH, you agree that you will not share the transaction info in public i.e. the transaction will remain
anonymous, nor will you use it as a leverage to prioritise your requested features. I accept feature requests without
any donations, and they are prioritised according to my preferences.

You can also donate me using Open Collective: https://opencollective.com/muntashir

**App Manager is open for funding/grants.** If you are an organisation interested in funding it you can contact me
directly at muntashirakon [at] riseup [dot] net (FINGERPRINT: `7bad37c2981e41f8f6abea7f58f0b4f26c346fce`).

## Credits and Libraries

A list of credits and libraries are available in the **About** section of the app.
