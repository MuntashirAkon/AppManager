# App Manager
![Debug Build](https://github.com/MuntashirAkon/AppManager/workflows/Debug%20Build/badge.svg)
[![Translation status](https://hosted.weblate.org/widgets/app-manager/-/svg-badge.svg)](https://hosted.weblate.org/engage/app-manager/)
[![GitHub release (including pre-releases)](https://img.shields.io/github/v/release/MuntashirAkon/AppManager?include_prereleases)](https://github.com/MuntashirAkon/AppManager/releases/latest)
[![F-Droid](https://img.shields.io/f-droid/v/io.github.muntashirakon.AppManager)](https://f-droid.org/packages/io.github.muntashirakon.AppManager)
![GitHub Repo Size](https://img.shields.io/github/repo-size/MuntashirAkon/AppManager)
[![TG Group](https://img.shields.io/badge/TG-Group-blue?logo=telegram)](https://t.me/AppManagerAndroid)
[![TG Channel](https://img.shields.io/badge/TG-Channel-blue?logo=telegram)](https://t.me/AppManagerChannel)
![Twitter @AMUpdateChannel](https://img.shields.io/twitter/follow/AMUpdateChannel?label=%40AMUpdateChannel)

Yet another Android package manager and viewer but...

- Copylefted libre software (GPLv3+)
- Material design (and a nice UI)
- No useless permissions
- Does not connect to the Internet (the permission is required for ADB mode)
- Displays as much info as possible in the main window
- Lists activities, broadcast receivers, services, providers, permissions, signatures, shared libraries, etc. of any app
- Launch (exportable) activities, create (customizable) shortcuts
- Block any activities, broadcast receivers, services or providers you like with native import/export as well as Watt and Blocker import support (requires root)
- Revoke permissions considered dangerous (requires root/ADB)
- Disable app ops considered dangerous (requires root/ADB)
- Scan for trackers in apps and list (all or only) tracking classes (and their code dump)
- Generate dynamic manifest for any app
- View/edit/delete shared preferences of any app (requires root)
- Display running processes/apps (requires root/ADB)
- Display your app usage, data usage and app storage info (requires “Usage Access” permission)
- APK files can be shared (hence the use of a provider)
- Clear app data or app cache (requires root/ADB)
- Batch operations: clear app data, disable run in background, disable/kill/uninstall apps
- One-click operations: block ads/tracker components, block components by signature, block multiple app ops

…and other minor features such as installing/uninstalling/updating/enabling/disabling apps, displaying app installation info, opening on F-Droid, Aurora Droid or Aurora Store combining the features of 5 or 6 apps any tech-savvy person needs.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
height="80" />](https://f-droid.org/packages/io.github.muntashirakon.AppManager)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
alt="Get it on IzzyOnDroid"
height="80" />](https://apt.izzysoft.de/fdroid/index/apk/io.github.muntashirakon.AppManager)

App Manager Docs: https://muntashirakon.github.io/AppManager

Telegram Support Group: https://t.me/AppManagerAndroid

Telegram Update Channel: https://t.me/AppManagerChannel

Follow **@AMUpdateChannel** on Twitter: https://twitter.com/AMUpdateChannel

### Translations

Translate **App Manager** at _Weblate_: https://hosted.weblate.org/engage/app-manager/

Translate **App Manager Docs** at _Crowdin_: https://crwd.in/app-manager-docs

[![Translation status](https://hosted.weblate.org/widgets/app-manager/-/multi-auto.svg)](https://hosted.weblate.org/engage/app-manager/)

### Mirrors

**GitLab**: https://gitlab.com/muntashir/AppManager

**Riseup**: https://0xacab.org/muntashir/AppManager

**Codeberg**: https://codeberg.org/muntashir/AppManager

### Screenshots

#### Light

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/13.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" height="500dp" />

#### Dark

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/14.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" height="500dp" /><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" height="500dp" />

### Build Instructions
* **System Requirements:** Any PC/Mac with 4GB RAM (8GB recommended)
* **Operating System:** Linux/macOS (no support for Windows)
* **Software:** Android Studio, gradle
* Active internet connection

#### macOS
- Install command line tools: (No need to install Xcode)
  ```bash
  xcode-select --install
  ```
- Install `gnu-sed`: (Goto https://brew.sh/ if you don't have `brew` installed)
  ```bash
  brew install gnu-sed
  ```

#### Linux
You need to install development tools.

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

#### Clone and Build App Manager
1. Clone the repo along with submodules:
    ```bash
    git clone --recurse-submodules https://github.com/MuntashirAkon/AppManager.git
    ```
    You can use the `--depth 1` argument if you don't want to clone past commits.
2. Open the project **AppManager** using Android Studio. Android Studio should start syncing automatically. It will also download all the necessary files automatically (provided you have Internet connection).
3. Build debug version of App Manager from Menu > Build > Make Project or from the terminal:
    ```
    ./gradlew assembleDebug
    ```

### Contributing
You are welcome contribute to App Manager (also known as, AM)! This doesn't mean that you need coding skills. You can help AM by creating helpful issues, attending discussions, improving documentations and translations, adding unrecognised libraries or ad/tracking signatures, reviewing the source code as well as reporting security vulnerabilities. But if you are going to contribute to AM with your coding skills, please read the following:
- If you're going to implement or work on any specific feature, please inform me before doing so. Due to the complex nature of the project, integrating a new feature could be challenging.
- You're absolutely welcome to fix issues or mistakes but AM's code base changes a lot in almost every day. Therefore, if you are requested to make changes in your pull request but fail to address them within 2 (two) days, your pull request may be closed depending on the importance of the request. This instruction will be removed once AM's code base becomes stable.

**Note:** Repositories located in sites other than GitHub are currently considered mirrors and PR/MR submitted there will not be accepted. Instead, you can submit patches (as `.patch` files) via email attachment. My email address is muntashirakon [at] riseup [dot] net. But beware that such emails may be publicly accessible in future. 

### Credits and Libraries
A list of credits and libraries are available in the **About** section of the app.
