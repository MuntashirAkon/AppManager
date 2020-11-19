---
next: ./app-details-page
prev: ./adb-over-tcp
sidebarDepth: 2
---

# 主界面

主界面列出了所有已安装的应用（或任何第三方应用程序提供的应用列表）以及已有备份的应用。 单击任何已安装的应用项目打开相应的 [应用详情][4] 页面。 使用菜单中的 [排序](#排序) 选项，可用多种方式排序。排序方式在关闭应用后仍然保存。 还可以根据您的需要，使用菜单中的 [过滤](#筛选) 选项筛选项目。 你可以使用多个条件进行过滤。 还可以使用搜索按钮，通过应用名称或包名过滤应用。

::: details 目录
[[toc]]
:::

## 批量操作
批量操作在主界面也可用（这些操作大多数需要 root 或 [ADB over TCP][1] ）。 要启动多选模式，请点击任何应用图标或长按任何应用。 之后你可以使用常规点击操作来继续选择更多应用。 这些操作包括：
- 备份 apk 文件（无需 root）
- 备份/恢复/删除应用数据 (apk、dex、应用数据等)
- 清除应用数据
- 禁用/卸载/强行停止应用
- 禁止应用在后台运行，拦截广告和追踪器
- 导出规则

## 颜色代码含义
Here's a list of colors used in this page and their meaning:
- 多选模式下被选中的应用：<code style="background-color: #FCEED1; color: #000">浅灰橙（亮色模式下）</code>或<code style="background-color: #091F36; color: #FFF">深蓝（暗色模式下）</code>
- 停用的应用：<code style="background-color: #FF8A80; color: #000">浅红（亮色模式下）</code>或<code style="background-color: #4F1C14; color: #FFF">深红（暗色模式下）</code>
- debug 模式下的应用：<code style="background-color: yellow; color: #000">黄色星标</code>
- 应用可读取日志（已授权）：<code style="color: #E05915">橙色的日期</code>
- 用户 ID 在应用间被共享：<code style="color: #E05915FF">橙色的UID</code>
- 使用明文网络通信（如 HTTP）：<code style="color: #E05915FF">橙色的 SDK/Size</code>
- 应用禁止清除数据：<code style="color: red">红色</code>
- 已（强行）停止运行的应用：<code style="color: #09868BFF">深蓝绿色的包名</code>
- 不活跃的应用：<code style="color: #09868BFF">深蓝绿色的版本号</code>
- 常驻应用（如一直运行的应用）：<code style="color: magenta">紫红</code>

## 应用类型
**用户程序**或**系统程序**遵循以下代码规则：
- `X` - 应用支持多种架构，如32位，64位等。
- `0` - 应用不含代码
- `°` - 应用处于休眠状态
- `#` - 应用已请求大量内存
- `?` - 应用已请求安全模式下的 VM

## 版本信息
版本名后跟以下前缀：
- `_` - 无硬件加速
- `~` - 仅测试模式
- `debug` - Debuggable 应用

## 选项菜单
Options menu has several options which can be used to sort, filter the listed apps as well as navigate to different pages.

### 排序
Apps listed on the main page can be sorted in different ways. The sorting preference is preserved which means the apps will be sorted the same way that was sorted in the previous launch. Regardless of your sorting preference, however, the apps are first sorted alphabetically to prevent random results.
- _User app first_ - User apps will appear first
- _App label_ - Sort in ascending order based on the app labels (also known as app name). This is the default sorting preference
- _Package name_ - Sort in ascending order based on package names
- _Last update_ - Sort in descending order based on the package update date (or install date if it is a newly installed package)
- _Shared user ID_ - Sort in descending order based on kernel user id
- _App size/sdk_ - Sort in ascending order based on app sdk (sorting by app size in not currently available)
- _Signature_ - Sort in ascending order based on app's signing info
- _Disabled first_ - List disabled apps first
- _Blocked first_ - Sort in descending order based on the number of blocked components

### 筛选
Apps listed on the main page can be filtered in a number of ways. Like sorting, filtering preferences are stored as well and retained after a relaunch.
- _User apps_ - List only user apps
- _System apps_ - List only system apps
- _Disabled apps_ - List only disabled apps
- _Apps with rules_ - List only apps with blocking rules

Unlike sorting, you can filter using more than one option. For example, you can list all disabled user apps by filtering app lists using _User apps_ and _Disabled apps_. This is also useful for batch operations where you can filter all users apps to carry out certain operation.

### 一键操作
**1-Click Ops** stands for **One-Click Operations**. You can directly open the corresponding page by clicking on this option.

_See also: [1-Click Ops Page][5]_

### 应用使用情况
App usage statistics such as _screen time_, _data usage_ (both mobile and Wifi), _number of times an app was opened_ can be accessed by clicking on the **App Usage** option in the menu (requires _Usage Access_ permission).

### 正在运行的应用
A list of running apps or processes can be viewed by clicking on the **Running Apps** option in the menu (requires root or [ADB][1]). Running apps or processes can also be force-closed or killed within the resultant page.

### APK Updater
If you have the app [APK Updater][3] installed, you can use the corresponding option in the menu to open the app directly. The option is hidden if you do not have it installed.

### Termux
If you have [Termux][2] installed, you can directly go to the running session or open a new session using the **Termux** option in the menu.

### 设置
You can go to in-app [Settings][settings] by clicking on the corresponding option.

[1]: ./adb-over-tcp.md
[2]: https://github.com/termux/termux-app
[3]: https://github.com/rumboalla/apkupdater
[4]: ./app-details-page.md
[5]: ./one-click-ops-page.md
[settings]: ./settings-page.md
