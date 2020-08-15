# ADB over TCP

Many root-only features can still be used by enabling ADB over TCP. To do that, a PC or Mac is required with ADB tools installed, and an Android phone with Developer Mode &amp; USB debugging enabled
([tutorial][1]).

After fulfilling the above requirement, the phone has to be connected to the PC or Mac
using a data cable. Then, in a command prompt, power shell or terminal, the following
command has to be run:

``` sh
adb tcpip 5555
```

After that, App Manager has to be relaunched.

In some Android phones, an alert prompt will
be appeared with a message **Allow USB Debugging** in which case, check _Always allow
from this computer_ and click **Allow**.

::: tip
In some other Android phones, the data cable
may need to be connected or disconnected in order for it to work.
:::

::: warning
You cannot disable developer mode or USB debugging after enabling ADB over TCP. 
:::

To stop ADB over TCP, connect your phone to the PC or Mac and run the following command:

```sh
adb kill-server
```

::: warning
ADB over TCP will be disabled after a restart. In that case, you have to run the stated command again to enable it.
:::

[1]: https://www.xda-developers.com/install-adb-windows-macos-linux/
