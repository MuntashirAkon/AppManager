---
sidebarDepth：2
---
# 基于TCP的ADB

仍然可以通过启用基于TCP的ADB来使用许多root-only功能。 为了做到这一点，安装了 Android 平台工具需要 PC 或 Mac 设备，并且开启了带有开发者选项 & USB 调试功能的 Android 手机。

::: tip Root 用户
要在 TCP 上使用 ADB , 您必须撤销应用管理器的Root权限并重启您的设备。 您可能会看到 _在 ADB 模式_ 消息上工作而不会重启，但它并不完全正确。 服务器 (系统与 App Manager之间的接口) 仍然在 root 模式下运行。 这是一个已知的问题，将在未来版本的应用管理器中修复。
:::

_另见： [常见问题：TCP上的 ADB][faq_aot]_

::: details 目录
[[toc]]
:::

## 1。 启用开发者选项

### 1.1. 开发者选项的位置
**开发者选项** 位于Android **设置**, 要么直接靠近页面底部(大部分ROM)，要么在一些其他设置下, 如 **系统** (Lineage OS, 8. Yusu Zenfone +, **系统** > **高级** (Google Pixel), **附加设置** (Xiaomi MIUI) Oppo ColorOS, **更多设置** (Vivo FuntouchOS), **More** (ZTE Nubia). 与其他选项不同，在用户明确启用之前它是不可见的。 如果开发者选项已启用，您也可以使用 Android **设置** 中的搜索框来定位它。

### 1.2. 如何启用开发者选项
此选项也可在 Android **设置** 中使用，但与开发者选项的位置相同。 它也因设备不同而不同。 但一般来说， 您必须找到 **构建号码** (或 **MIUI 版本** MIUM 和 **软件版本** Vivo FuntouchOS **Oppo ColorOS版本** 并点击至少7(7)次，直到您最后收到消息 _您现在是开发者_ (您可能会被提示插入pin/password/password/pattern或解决captchas)。 在大多数设备中，它位于设置页面的底部，关于 **关于电话**。 但找到它的最佳办法是使用搜索框。

## 2. 启用USB调试
在 [定位开发者选项](#_1-1-开发者选项的位置)后，启用 **开发者选项** (如果还没有) 然后向下滚动直到您找到选项 **USB 调试**。 使用右侧的切换按钮来启用它。 此刻，您可能会收到一个警报提示, 您可能需要点击 _确定_ 才能真正启用它。 您可能还必须根据设备供应商和ROM启用其他选项。 下面是一些示例：

### 2.1. 小米 (MIUI)
启用 **USB 调试(安全设置)**。

### 2.2. 华为 (EMUI)
启用 **同时允许 ADB 调试只能充电模式**。 当连接到您的 PC 或 Mac 时，您可能会得到提示说 **允许访问设备数据吗？** 在这种情况下点击 **是的，允许访问**。

::: tip 提示
通常情况下，系统可以自动禁用 **USB 调试** 模式。 如果是这样，则重复上述程序。 
:::

### 2.3. LG
请确保您已启用 **USB 网络共享**。

### 2.4 排除故障
如果 **USB 调试** 灰色，您可以做以下工作：
1. 在通过 USB 电缆连接您的手机到 PC 或 Mac 之前，请确保您启用 USB 调试功能
2. 通过 USB 电缆连接到 PC 或 Mac 后启用 USB 网络共享
3. (对于三星) 如果你的设备正在运行 KNOX，你可能必须遵循一些额外的步骤。 查看官方文件或咨询支持以获取更多支持


## 3. 在 PC 或 Mac 上设置 ADB
若要通过 TCP 启用 ADB ，您必须在您的 PC 或 Mac 中设置 ADB。 **_Lineage OS 用户可以跳至 [第4.1节](#_4-1-lineage-os)_**

### 3.1. Windows
1. 下载最新版本的 [Windows Android SDK 平台工具][sdk_pt_win]
2. 提取zip文件的内容到任何目录(如 `C:\adb`)，然后使用 _文件资源管理器_ 导航到该目录。
3. 从此目录打开 **命令提示** 或 **PowerShell** 您可以从开始菜单手动操作，也可以按住 `Shift` 并按住在 _文件资源管理器中的目录里的_ 然后点击 _打开命令窗口_ 或点击 _打开这里的 PowerShell 窗口_ (取决于您已经安装的内容)。 您现在可以通过输入 `adb` (命令提示) 或 `./adb` (PowerShell) 来访问 ADB。 暂时不要关闭此窗口

### 3.2. macOS
1. 为macOS 下载最新版本的 [Android SDK 平台工具][sdk_pt_mac]
2. 点击压缩文件中的内容解压缩到目录中。 此后，使用 _Finder_ 导航到该目录并定位 `adb`
3. 打开 **终端** 使用 _Launchpad_ 或 _Spotlight_ 和拖放 `adb` 从 _Finder_ 窗口打开到 _终端_ 窗口 暂不关闭 _终端_ window

::: tip 提示
如果你不害怕使用命令行, 这里是一个单行本:
```sh
cd ~/Downloads && curl -o platform-tools.zip -L https://dl.google.com/android/repository/platform-tools-latest-darwin.zip && unzip platform-tools.zip && rm platform-tools.zip && cd platform-tools
```
然后，您可以在相同 _Terminal_ 窗口中只需输入 `./adb` 即可访问 ADB。 
:::

### 3.3. Linux
1. 打开您最喜欢的终端仿真器。 在大多数图形界面中, 您可以同时按住 `控制`, `修改` 和 `T` 打开它
2. 运行以下命令：
```sh
cd ~/Downloads && curl -o platform-tools.zip -L https://dl.google.com/android/repository/platform-tools-latest-linux.zip && unzip platform-tools.zip && rm platform-tools.zip && cd platform-tools
```
3. 如果它成功，您可以简单地输入 `。 adb` 在 _中相同的_ 终端模拟窗口或类型 `~/Downloads/platform-tools/adb` 在任何终端模拟器中访问 ADB。

## 4. 通过 TCP 配置 ADB

### 4.1. Lineage OS
Lineage OS (或其衍生工具)用户可以使用开发者选项直接通过 TCP 启用 ADB。 若要启用此功能，请前往 **开发者选项**, 向下滚动直到您找到 **ADB 通过网络**。 现在，使用右侧的切换按钮来启用它，然后继续到 [4.3](#_4-3-在应用管理器上启用-adb-模式)。

### 4.2. 通过PC或Mac启用基于TCP的ADB
对于其它ROM，您可以使用命令提示/PowerShell/终端仿真器来做到这一点，你已经在上一节第3步打开。 在本节中，我将使用 `adb` 来表示 `adb`, `adb` 或您需要根据您的平台和软件在上一节中使用的任何其他命令。
1. 使用 USB 线连接您的设备到 PC 或 Mac 。 对于某些设备，有必要打开 _文件传输模式 (MTP)_
2. 要确认一切都在正常工作，请在您的终端中输入 `adb devices`。 如果您的设备已成功连接，您将会看到这样的东西：
    ```
    List of devices attached
    xxxxxxxx  device
    ```
    ::: 小贴士 在一些安卓手机中 警告提示将会出现在消息 **允许USB调试** 在这种情况下。 检查 _始终允许使用此计算机_ 并点击 **允许**. :::
3. 最后，运行以下命令以启用基于TCP的ADB：
    ``` sh
    adb tcpip 5555
    ```
    ::: danger 警告
    你不能在启用基于TCP的ADB后禁用开发者选项或USB 调试。 
    :::

### 4.3. 在应用管理器上启用 ADB 模式
启用基于TCP的ADB(在以前的子节) 后，打开应用程序管理器 (AM)。 您应该在底部看到 **正在使用 ADB 模式** 的提示消息。 如果不是，请从最近任务中移除AM，然后再次从启动器中打开AM。 如果你看到弹出消息，你可以安全地[通过TCP停止ADB](#_4-4-基于tcp的adb)。

::: tip 小贴士
在一些安卓手机中 USB 电缆可能需要连接或断开与PC的连接才能工作。 
:::

::: warning 警告
TCP上的 ADB 将在重启后被禁用。 在这种情况下，您必须再次按照 [第4.2节](#_4-2-通过pc或mac启用基于tcp的adb)
:::

### 4.4. 基于TCP的ADB
为了确保设备安全，您应该在 AM 侦测后立即在 TCP 上停止 ADB。 要做到这一点，请将您的设备连接到 PC 或 Mac 并运行以下命令：
```sh
adb kill-server
```
将 `adb` 替换为 `./adb` 或者您必须在前几步中使用的任何其他命令。

对于lineage OS，您可以在开发者选项中通过网络</strong> 关闭 **ADB。</p>

## 5. 参考
1. [如何在 Windows、 macOS和Linux 上安装 ADB](https://www.xda-developers.com/install-adb-windows-macos-linux)
2. [Android Debug Bridge (adb)](https://developer.android.com/studio/command-line/adb)
3. [如何修复 USB 调试？](https://www.syncios.com/android/fix-usb-debugging-grey-out.html)

[faq_aot]: ../faq/adb.md
[sdk_pt_win]: https://dl.google.com/android/repository/platform-tools-latest-windows.zip
[sdk_pt_mac]: https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
