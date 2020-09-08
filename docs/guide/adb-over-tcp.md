---
sidebarDepth: 2
---
# ADB over TCP

Many root-only features can still be used by enabling ADB over TCP. To do that, a PC or Mac is required with Android platform-tools installed, and an Android phone with developer options & USB debugging enabled.

_See also: [FAQ: ADB over TCP][faq_aot]_

::: details Table of Contents
[[toc]]
:::

## 1. Enable developer options

### 1.1. Location of developer options
**Developer options** is located in Android **Settings**, either directly near the bottom of the page (in most ROMs) or under some other settings such as **System** (Lineage OS, Asus Zenfone 8.0+), **System** > **Advanced** (Google Pixel), **Additional Settings** (Xiaomi MIUI, Oppo ColorOS), **More Settings** (Vivo FuntouchOS), **More** (ZTE Nubia). Unlike other options, it is not visible until explicitly enabled by the user. If developer options is enabled, you can use the search box in Android **Settings** to locate it as well.

### 1.2. How to enable developer options
This option is available within Android **Settings** as well but like the location of the developer options, it also differs from device to device. But in general, you have to find **Build number** (or **MIUI version** for MIUI ROMs and **Software version** for Vivo FuntouchOS, **Version** for Oppo ColorOS) and tap it at least 7 (seven) times until you finally get a message saying _You are now a developer_ (you may be prompted to insert pin/password/pattern or solve captchas at this point). In most devices, it is located at the bottom of the settings page, inside **About Phone**. But the best way to find it is to use the search box.

## 2. Enable USB debugging
After [locating the developer options](#_1-1-location-of-developer-options), enable **Developer option** (if not already). After that, scroll down a bit until you will find the option **USB debugging**. Use the toggle button on the right hand side to enable it. At this point, you may get an alert prompt where you may have to click _OK_ to actually enable it. You may also have to enable some other options depending on device vendor and ROM. Here are some examples:

### 2.1. Xiaomi (MIUI)
Enable **USB debugging (security settings)** as well. 

### 2.2. Huawei (EMUI)
Enable **Allow ADB debugging in charge only mode** as well. When connecting to your PC or Mac, you may get a prompt saying **Allow access to device data?** in which case click **YES, ALLOW ACCESS**.

::: tip Notice
Often the **USB debugging** mode could be disabled automatically by the system. If that's the case, repeat the above procedure.
:::

### 2.3. LG
Make sure you have **USB tethering** enabled.

### 2.4. Troubleshooting
In case **USB Debugging** is grayed out, you can do the following:
1. Make sure you enabled USB debugging before connecting your phone to the PC or Mac via USB cable
2. Enable USB tethering after connecting to PC or Mac via USB cable
3. (For Samsung) If you're device is running KNOX, you may have to follow some additional steps. See official documentations or consult support for further assistant 


## 3. Setup ADB on PC or Mac
In order to enable ADB over TCP, you have to setup ADB in your PC or Mac. **_Lineage OS users can skip to [section 4.1](#_4-1-lineage-os)._**

### 3.1. Windows
1. Download the latest version of [Android SDK Platform-Tools][sdk_pt_win] for Windows
2. Extract the contents of the zip file into any directory (such as `C:\adb`) and navigate to that directory using _File Explorer_
3. Open **Command Prompt** or **PowerShell** from this directory. You can do it manually from the start menu or by holding `Shift` and Right clicking within the directory in _File Explorer_ and then clicking either on _Open command window here_ or on _Open PowerShell window here_ (depending on what you have installed). You can now access ADB by typing `adb` (Command Prompt) or `./adb` (PowerShell). Do not close this window yet

### 3.2. macOS
1. Download the latest version of [Android SDK Platform-Tools][sdk_pt_mac] for macOS
2. Extract the contents of the zip file into a directory by clicking on it. After that, navigate to that directory using _Finder_ and locate `adb`
3. Open **Terminal** using _Launchpad_ or _Spotlight_ and drag-and-drop `adb` from the _Finder_ window into the _Terminal_ window. Do not close the _Terminal_ window yet

::: tip Tip
If you are not afraid to use command line, here's a one liner:
```sh
cd ~/Downloads && curl -o platform-tools.zip -L https://dl.google.com/android/repository/platform-tools-latest-darwin.zip && unzip platform-tools.zip && rm platform-tools.zip && cd platform-tools
```
After that, you can simply type `./adb` in the in same _Terminal_ window to access ADB.
:::

### 3.3. Linux
1. Open your favourite terminal emulator. In most GUI-distros, you can open it by holding `Control`, `Alter` and `T` at the same time 
2. Run the following command:
```sh
cd ~/Downloads && curl -o platform-tools.zip -L https://dl.google.com/android/repository/platform-tools-latest-linux.zip && unzip platform-tools.zip && rm platform-tools.zip && cd platform-tools
```
3. If it is successful, you can simply type `./adb` in the in _same_ terminal emulator window or type `~/Downloads/platform-tools/adb` in any terminal emulator to access ADB.

## 4. Configure ADB over TCP

### 4.1. Lineage OS
Lineage OS (or its derivatives) users can directly enable ADB over TCP using the developer options. To enable that, go to the **Developer options**, scroll down until you find **ADB over Network**. Now, use the toggle button on the right hand side to enable it and proceed to [section 4.3](#_4-3-enable-adb-mode-on-app-manager).

### 4.2. Enable ADB over TCP via PC or Mac
For other ROMs, you can do this using the command prompt/PowerShell/terminal emulator that you've opened in the step 3 of the previous section. In this section, I will use `adb` to denote `./adb`, `adb` or any other command that you needed to use based on your platform and software in the previous section.
1. Connect you device to your PC or Mac using a USB cable. For some devices, it is necessary to turn on _File transfer mode (MTP)_ as well
2. To confirm that everything is working as expected, type `adb devices` in your terminal. If your device is connected successfully, you will see something like this:
    ```
    List of devices attached
    xxxxxxxx  device
    ```
    ::: tip Notice
    In some Android phones, an alert prompt will be appeared with a message **Allow USB Debugging** in which case, check _Always allow from this computer_ and click **Allow**.
    :::
3. Finally, run the following command to enable ADB over TCP:
    ``` sh
    adb tcpip 5555
    ```
    ::: danger Danger
    You cannot disable developer options or USB debugging after enabling ADB over TCP. 
    :::

### 4.3. Enable ADB mode on App Manager
After enabling ADB over TCP (in the previous subsections), open App Manager (AM). You should see **working on ADB mode** toast message at the bottom. If not, remove AM from the recents and open AM again from the launcher. If you see the toast message, you can safely [stop ADB over TCP](#_4-4-stop-adb-over-tcp).

::: tip Notice
In some Android phones, the USB cable may need to be connected or disconnected from the PC in order for it to work.
:::

::: warning Warning
ADB over TCP will be disabled after a restart. In that case, you have to follow [section 4.2](#_4-2-enable-adb-over-tcp-via-pc-or-mac) again.
:::

### 4.4. Stop ADB over TCP
In order to ensure device security, you should stop ADB over TCP right after AM detects it. To do that, connect your device to your PC or Mac and run the following command:
```sh
adb kill-server
```
Replace `adb` with `./adb` or any other command that you had to use in previous steps.

For lineage OS, you can turn off **ADB over Network** in developer options.

## 5. References
1. [How to Install ADB on Windows, macOS, and Linux](https://www.xda-developers.com/install-adb-windows-macos-linux)
2. [Android Debug Bridge (adb)](https://developer.android.com/studio/command-line/adb)
3. [How to fix USB debugging greyed out?](https://www.syncios.com/android/fix-usb-debugging-grey-out.html)

[faq_aot]: ../faq/adb.md
[sdk_pt_win]: https://dl.google.com/android/repository/platform-tools-latest-windows.zip
[sdk_pt_mac]: https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
