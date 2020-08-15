---
prev: ./
sidebarDepth: 2
---
# App Ops

*Go to the [related issue](https://github.com/MuntashirAkon/AppManager/issues/17) for discussion.*

::: details Table of Contents
[[toc]]
:::

## Background
**App Ops** (short hand for **Application Operations**) are used by Android system (since Android 4.3) to control application permissions. The user *can* control some permissions, but only the permissions that are considered dangerous (and Google thinks knowing your phone number isn't a dangerous thing). So, app ops seems to be the one we need if we want to install apps like Facebook and it's Messenger (which literary records everything) and still want *some* privacy and/or security. Although certain features of app ops were available in Settings and later in hidden settings in older version of Android, it's completely hidden in newer versions of Android and is continued to be kept hidden. Now, any app with **android.Manifest.permission.GET_APP_OPS_STATS** permission can get the app ops information for other applications but this permission is hidden from users and can only be enabled using ADB or root. Still, the app with this permission cannot grant or revoke permissions (actually mode of operation) for apps other than itself (with limited capacity, of course). To modify the ops of other app, the app needs **android.Manifest.permission.UPDATE_APP_OPS_STATS** permissions which isn't accessible via _pm_ command. So, you cannot grant it via root or ADB, the permission is only granted to the system apps. There are very few apps who support disabling permissions via app ops. The best one to my knowledge is [AppOpsX][1]. The main (visible) difference between my app (AppManager) and this app is that the later also provides you the ability to revoke internet permissions (by writing ip tables). Another difference is that the author used the hidden API to access/grant/revoke ops whereas I used [_appops_](#appops-command-line-interface) command-line tool to do that. I did this because of the limit of [Reflection][2] that Android recently imposed which rendered many hidden APIs unusable (there are some hacks but they may not work after the final release of R, I believe). One crucial problem that I faced during developing an API for App Ops is the lack of documentation in English language.

[1]: https://github.com/8enet/AppOpsX
[2]: https://stackoverflow.com/questions/37628

## Introduction to App Ops

![How AppOps Works](/assets/how_app_ops_work.png)

The figure (taken from [this article][3]) above describes the process of changing and processing permission. [**AppOpsManager**](#appopsmanager) can be used to manage permissions in Settings app. **AppOpsManager** is also useful in determining if a certain permission (or operation) is granted to the application. Most of the methods of **AppOpsManager** are accessible to the user app but unlike a system app, it can only be used to check permissions for any app or for the app itself and start or terminating certain operations. Moreover, not all operations are actually accessible from this Java class. **AppOpsManager** holds all the necessary constants such as [_OP\_*_](#op-constants), `OPSTR_*`, [_MODE\_*_](#mode-constants) which describes operation code, operation string and mode of operations respectively. It also holds necessary data stuctures such as [**PackageOps**](#packageops) and **OpEntry**. **PackageOps** holds **OpEntry** for a package, and **OpEntry**, as the name suggests, describes each operation. Under the hood, **AppOpsManager** calls **AppOpsService** to perform any real work.

[**AppOpService**][5] is completely hidden from a user application but acessible to the system applications. As seen in the picture, this is the class that does the actual management stuff. It contains data structures such as **Ops** to store basic package info and **Op** which is similar to **OpEntry** of **AppOpsManager**. It also has **Shell** which is actually the source code of the _appops_ command line tool. It writes configurations to or read configurations from [/data/system/appops.xml](#appops-xml). System services calls **AppOpsService** to find out what an application is allowed and what is not allowed to perform, and **AppOpsService** determines these permissions by parsing `/data/system/appops.xml`. If no custom values are set in _appops.xml_, it returns the default mode available in **AppOpsManager**.

[3]: https://translate.googleusercontent.com/translate_c?depth=2&pto=aue&rurl=translate.google.com&sl=auto&sp=nmt4&tl=en&u=https://www.cnblogs.com/0616--ataozhijia/p/5009718.html&usg=ALkJrhgSo4IcKp2cXJlqttXuiRJZGa_jnw
[5]: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/appop/AppOpsService.java
[6]: https://translate.googleusercontent.com/translate_c?depth=1&pto=aue&rurl=translate.google.com&sl=auto&sp=nmt4&tl=en&u=https://lishiwen4.github.io/android/appops&usg=ALkJrhg5elsQfV5O3bnzk7oP5NxWV4Qr6g


## AppOpsManager
[AppOpsManager][4] stands for application operations manager. It consists of various constants and classes to modify app operations. Official documentation can be found [here][11].

[4]: https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/AppOpsManager.java

[11]: https://developer.android.com/reference/android/app/AppOpsManager

### OP_* Constants
`OP_*` are the integer constants starting from `0`. `OP_NONE` implies that no operations are specified whereas `_NUM_OP` denotes the number of operations defined in `OP_*` prefix.  These denotes each operations. But these operations are not necessarily unique. In fact, there are many operations that are actually a single operation denoted by multiple `OP_*` constant (possibly for future use). Vendors may define their own op based on their requirements. MIUI is one of the vendors who are known to do that.

_Sneak-peek of `OP_*`:_
``` java{1,10}
public static final int OP_NONE = -1;
public static final int OP_COARSE_LOCATION = 0;
public static final int OP_FINE_LOCATION = 1;
public static final int OP_GPS = 2;
public static final int OP_VIBRATE = 3;
...
public static final int OP_READ_DEVICE_IDENTIFIERS = 89;
public static final int OP_ACCESS_MEDIA_LOCATION = 90;
public static final int OP_ACTIVATE_PLATFORM_VPN = 91;
public static final int _NUM_OP = 92;
```

Whether an operation is unique is defined by [`sOpToSwitch`][7]. It maps each operation to another operation or to itself (if it's a unique operation). For instance, `OP_FINE_LOCATION` and `OP_GPS` are mapped to `OP_COARSE_LOCATION`.

Each operation has a private name which are described by [`sOpNames`][10]. These names are usually the same names as the constants without the `OP_` prefix. Some operations have public names as well which are described by `sOpToString`. For instance, `OP_COARSE_LOCATION` has the public name **android:coarse_location**.

As a gradual process of moving permissions to app ops, there are already many permissions that are defined under some operations. These permissions are mapped in [`sOpPerms`][8]. For example, the permission **android.Manifest.permission.ACCESS_COARSE_LOCATION** is mapped to `OP_COARSE_LOCATION`. Some operations may not have any associated permissions which have `null` values.

As described in the previous section, operations that are configured for an app are stored at [/data/system/appops.xml](#appops-xml). If an operation is not configured, then whether system will allow that operation is determined from [`sOpDefaultMode`][9]. It lists the _default mode_ for each operation.

[7]: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/AppOpsManager.java;drc=44cbdec292c6b234d94aae59257721cf499989ba;bpv=1;bpt=1;l=211?q=AppOpsManager&ss=android%2Fplatform%2Fsuperproject&gsn=sOpToSwitch&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dandroid.app.AppOpsManager%238ffb80c9b09fce58d7fe1a0af7d50fd025765d8f41e838fa3bc2754dd99d9c48

[8]: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/AppOpsManager.java;drc=44cbdec292c6b234d94aae59257721cf499989ba;bpv=1;bpt=1;l=361?gsn=sOpPerms&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dandroid.app.AppOpsManager%23230bc1462b07a3c1575477761782a9d3537d75b4ea0a16748082c74f50bc2814

[9]: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/AppOpsManager.java;drc=44cbdec292c6b234d94aae59257721cf499989ba;bpv=1;bpt=1;l=410?gsn=sOpDefaultMode&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dandroid.app.AppOpsManager%23a8c8e4e247453a8ce329b2c1130f9c7a7f91e2b97d159c3e18c768b4d42f1b75

[10]: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/AppOpsManager.java;drc=44cbdec292c6b234d94aae59257721cf499989ba;bpv=1;bpt=1;l=311?gsn=sOpNames&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dandroid.app.AppOpsManager%234f77b221ad3e5d9212e217eadec0b78cd35717a3bf2d0f2bc642dea241e02d72

### MODE_* Constants
`MODE_*` constants also integer constants starting from `0`. These constants are assigned to each operations describing whether an app is authorised to perform that operation. These modes usually have associated names such as **allow** for `MODE_ALLOWED`, **ignore** for `MODE_IGNORED`, **deny** for `MODE_ERRORED` (a rather misonomer), **default** for `MODE_DEFAULT` and **foreground** for `MODE_FOREGROUND`.

_Default modes:_
``` java
/**
 * the given caller is allowed to perform the given operation.
 */
public static final int MODE_ALLOWED = 0;
/**
 * the given caller is not allowed to perform the given operation, and this attempt should
 * <em>silently fail</em> (it should not cause the app to crash).
 */
public static final int MODE_IGNORED = 1;
/**
 * the given caller is not allowed to perform the given operation, and this attempt should
 * cause it to have a fatal error, typically a {@link SecurityException}.
 */
public static final int MODE_ERRORED = 1 << 1;  // 2
/**
 * the given caller should use its default security check. This mode is not normally used
 */
public static final int MODE_DEFAULT = 3;
/**
 * Special mode that means "allow only when app is in foreground."
 */
public static final int MODE_FOREGROUND = 1 << 2;
```

Besides these default modes, vendors can set custom modes such as `MODE_ASK` (with the name **ask**) which is actively used by MIUI. MIUI also uses some other modes without any name associated with them. 


### PackageOps
**AppOpsManager.PackageOps** is a data structure to store all the **OpEntry** for a package. In simple terms, it stores all the customised operations for a package.

``` java
public static class PackageOps implements Parcelable {
  private final String mPackageName;
  private final int mUid;
  private final List<OpEntry> mEntries;
  ...
}
```
As can be seen above, it stores all **OpEntry** for a package as well as the corresponding package name and it's kernel user ID.


### OpEntry
**AppOpsManager.OpEntry** is a data structure that stores a single operation for any package.

``` java
public static final class OpEntry implements Parcelable {
    private final int mOp;
    private final boolean mRunning;
    private final @Mode int mMode;
    private final @Nullable LongSparseLongArray mAccessTimes;
    private final @Nullable LongSparseLongArray mRejectTimes;
    private final @Nullable LongSparseLongArray mDurations;
    private final @Nullable LongSparseLongArray mProxyUids;
    private final @Nullable LongSparseArray<String> mProxyPackageNames;
    ...
}
```
Here:
- `mOp`: Denotes one of the [`OP_*` constants](#op-constants).
- `mRunning`: Whether the operations is in progress (ie. the operation has started but not finished yet). Not all operations can be started or finished this way.
- `mMOde`: One of the [`MODE_*` constants](#mode-constants).
- `mAccessTimes`: Stores all the available access times
- `mRejectTimes`: Stores all the available reject times
- `mDurations`: All available access durations, checking this with `mRunning` will tell you for how long the app is performing a certain app operation.
- `mProxyUids`: No documentation found
- `mProxyPackageNames:` No documentation found

### Usage
TODO

## AppOpsService
TODO

##  appops.xml

Latest `appops.xml` has the following format: (This DTD is made by me and by no means perfect, has compatibility issues.)

```dtd
<!DOCTYPE app-ops [

<!ELEMENT app-ops (uid|pkg)*>
<!ATTLIST app-ops v CDATA #IMPLIED>

<!ELEMENT uid (op)*>
<!ATTLIST uid n CDATA #REQUIRED>

<!ELEMENT pkg (uid)*>
<!ATTLIST pkg n CDATA #REQUIRED>

<!ELEMENT uid (op)*>
<!ATTLIST uid
n CDATA #REQUIRED
p CDATA #IMPLIED>

<!ELEMENT op (st)*>
<!ATTLIST op
n CDATA #REQUIRED
m CDATA #REQUIRED>

<!ELEMENT st EMPTY>
<!ATTLIST st
n CDATA #REQUIRED
t CDATA #IMPLIED
r CDATA #IMPLIED
d CDATA #IMPLIED
pp CDATA #IMPLIED
pu CDATA #IMPLIED>

]>
```

The instructions below follows the exact order given above:
* `app-ops`: The root element. It can contain any number of `pkg` or package `uid`
  - `v`: (optional, integer) The version number (default: `NO_VERSION` or `-1`)
* `pkg`: Stores package info. It can contain any number of `uid`
  - `n`: (required, string) Name of the package
* Package `uid`: Stores package or packages info
  - `n`: (required, integer) The user ID
* `uid`: The package user ID. It can contain any number of `op`
  - `n`: (required, integer) The user ID
  - `p`: (optional, boolean) Is the app is a private/system app
* `op`: The operation, can contain `st` or nothing at all
  - `n`: (required, integer) The op name in integer, ie. AppOpsManager.OP_*
  - `m`: (required, integer) The op mode, ie. AppOpsManager.MODE_*
* `st`: State of operation: whether the operation is accessed, rejected or running (not available on old versions)
  - `n`: (required, long) Key containing flags and uid
  - `t`: (optional, long) Access time (default: `0`)
  - `r`: (optional, long) Reject time (default: `0`)
  - `d`: (optional, long) Access duration (default: `0`)
  - `pp`: (optional, string) Proxy package name
  - `pu`: (optional, integer) Proxy package uid

This definition can be found at [**AppOpsService**][5].

## appops command line interface
`appops` or `cmd appops` (on latest versions) can be accessible via ADB or root. This is an easier method to get or update any operation for a package (provided the package name is known). The help page of this command is self explanatory:

```
AppOps service (appops) commands:
help
  Print this help text.
start [--user <USER_ID>] <PACKAGE | UID> <OP> 
  Starts a given operation for a particular application.
stop [--user <USER_ID>] <PACKAGE | UID> <OP> 
  Stops a given operation for a particular application.
set [--user <USER_ID>] <[--uid] PACKAGE | UID> <OP> <MODE>
  Set the mode for a particular application and operation.
get [--user <USER_ID>] <PACKAGE | UID> [<OP>]
  Return the mode for a particular application and optional operation.
query-op [--user <USER_ID>] <OP> [<MODE>]
  Print all packages that currently have the given op in the given mode.
reset [--user <USER_ID>] [<PACKAGE>]
  Reset the given application or all applications to default modes.
write-settings
  Immediately write pending changes to storage.
read-settings
  Read the last written settings, replacing current state in RAM.
options:
  <PACKAGE> an Android package name or its UID if prefixed by --uid
  <OP>      an AppOps operation.
  <MODE>    one of allow, ignore, deny, or default
  <USER_ID> the user id under which the package is installed. If --user is not
            specified, the current user is assumed.
```
