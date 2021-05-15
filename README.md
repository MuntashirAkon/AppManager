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
- Lists activities, broadcast receivers, services, providers, permissions, signatures, shared libraries, etc. of any app
- Launch (exportable) activities and services
- Create (customizable) shortcuts of activities
- [Intercept activities](https://muntashirakon.github.io/AppManager/en/#x1-1220002.8)
- Scan for trackers and libraries in apps and list (all or only) tracking classes (and their code dump)
- View the manifest of an app
- Display your app usage, data usage, and app storage info (requires “Usage Access” permission)
- Install/uninstall APK files (including APKS, APKM and XAPK with OBB files)
- Share APK files
- Back up/restore APK files
- Batch operations
- One-click operations
- Logcat viewer
- [Profiles](https://muntashirakon.github.io/AppManager/en/#x1-710002.5) (including presets for quick debloating)
- View app usage along with mobile and wifi data usage
- Open app in Aurora Store or in your F-Droid client
- Sign APK files before installing them
- Backup encryption: OpenPGP via OpenKeychain, RSA (hybrid encryption with AES) and AES.

### Root/ADB-only features
- Revoke permissions considered dangerous by Android
- Deny or ignore app ops
- Display/kill/force-stop running processes/apps
- Clear app data or app cache

### Root-only features
- Block any activities, broadcast receivers, services, or providers of an app with native import/export as well as Watt and Blocker import support
- View/edit/delete shared preferences of any app
- Back up/restore apps with data, rules and extras (such as permissions, battery optimization, SSAID, etc.)
- System configuration, blacklisted or whitelisted apps
- View/change SSAID, net policy, battery optimization

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
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
alt="Get it on IzzyOnDroid"
height="80" />](https://apt.izzysoft.de/fdroid/index/apk/io.github.muntashirakon.AppManager)

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
* **System Requirements:** Any computer with 4GB RAM (8GB recommended)
* **Operating System:** Linux/macOS (no support for Windows)
* **Software:** Android Studio, Gradle
* Active internet connection

**Note:** For documentation build instruction,see [here](docs/raw/latex/README)


### macOS
The following steps are required only if you want to build APKS:
- Install Homebrew:
  ```bash
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  ```
- Install `bundletool`
  ```bash
  brew install bundletool
  ```

### Linux|GNU
- Install the development tools.
  For Debian/Ubuntu:
  ```bash
  sudo apt-get install build-essential
  ```
  For Fedora/CentOS/RHEL:
  ```bash
  sudo yum groupinstall "Development Tools"
  ```
  For Arch/Artix/Manjaro:
  ```bash
  sudo pacman -S base-devel
  ```
- Install [**bundletool-all.jar**](https://github.com/google/bundletool) if you want to build APKS, and make sure it is available as `bundletool` command. A quick way would be to create a file `bundletool` in `/usr/local/bin` directory with the following content:
  ```bash
  #!/usr/bin/env bash
  exec java -jar "/path/to/bundletool-all.jar" "$@"
  ```
  Make sure to replace `/path/to/bundletool-all.jar` with the actual path for **bundletool-all.jar**. Also, make the file executable:
  ```bash
  chmod +x /usr/local/bin/bundletool
  ```

### Clone and Build App Manager
1. Clone the repo along with submodules:
    ```bash
    git clone --recurse-submodules https://github.com/MuntashirAkon/AppManager.git
    ```
   You can use the `--depth 1` argument if you don't want to clone past commits.
2. Open the project **AppManager** using Android Studio/IntelliJ IDEA. The IDE should start syncing automatically. It will also download all the necessary dependencies automatically provided you have the Internet connection.
3. Build debug version of App Manager from _Menu_ > _Build_ > _Make Project_, or, from the terminal:
    ```
    ./gradlew packageDebugUniversalApk
    ```
   The command will generate a universal APK instead of a bundled app.

### Create Bundled App
In order to create a bundled app in APKS format, build Android App Bundle (AAB) first. Then run the following command:
```bash
./scripts/aab_to_apks.sh preRelease
```
Replace `prePelease` with `release` or `debug` based on your requirements. It will ask for keystore credentials interactively.

The script above will also generate a universal APK.

## Contributing
You are welcome contribute to App Manager! This doesn't mean that you need coding skills. You can help App Manager by creating helpful issues, attending discussions, improving documentations and translations, making icon for icon packs, adding unrecognised libraries or ad/tracking signatures, reviewing the source code, as well as reporting security vulnerabilities. If you are going to contribute to AM with your coding skills, please read the following:
- If you're going to implement or work on any specific feature, please inform me before doing so. Due to the complex nature of the project, integrating a new feature could be challenging.
- You're absolutely welcome to fix issues or mistakes, but App Manager's code base changes a lot almost every day. Therefore, if you are requested to make changes in your pull request but can't address them within 2 (two) days, your pull request may be closed depending on the importance of the request. This instruction will be removed once the code base is stable.

**Note:** Repositories located in sites other than GitHub are currently considered mirrors and PR/MR submitted there will not be accepted. Instead, you can submit patches (as `.patch` files) via email attachment. My email address is muntashirakon [at] riseup [dot] net. Beware that such emails may be publicly accessible in future. GitHub PRs will be merged manually using the corresponding patches. As a result, GitHub may falsely mark them _closed_ instead of _merged_. Make sure to sign-off your commits.

## Donation and Funding
**App Manager doesn't support any donations directly.** However, if you like my projects (App Manager being one of them), you can buy me a coffee by sending an anonymous donation to one of the following **Bitcoin** addresses:
```
33TDkWVv5EgwfKGJk7YaS2Ev1CBzBP9Sav
38bzvWDD99dJhXg9tC4yQEnGdnAKPtwSXG
3FHTxPoYa92dNJK6pkhwyVkMG8Vv3VpGpg
```
By sending me BTC, you agree that you will not share the transaction info in public i.e. the transaction will remain anonymous, nor will you use it as a leverage to prioritise your requested features. I accept feature requests without any donations, and they are prioritised according to my preferences.

**App Manager is open for funding/grants.** If you are an organisation interested in funding it you can contact me directly at muntashirakon [at] riseup [dot] net (FINGERPRINT: `7bad37c2981e41f8f6abea7f58f0b4f26c346fce`).

## Credits and Libraries
A list of credits and libraries are available in the **About** section of the app.
