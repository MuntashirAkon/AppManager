---
next: false
sidebarDepth: 2
---
# Miscellanea

::: details Table of Contents
[[toc]]
:::

## Any plans for Shizuku?
It would definitely be nice if I added support for Shizuku. But the problem is not with me or my app, it's them.

Although [Shizuku][shizuku] is an open source app, it doesn't use any license. Hence, it's a non-free (non-libre) software. Initially thinking it as an oversight, a number of people had [suggested the authors][shizuku_56] to add a license for their app as F-Droid scanners would not build a library without a free license. In response, the authors have separated the API library responsible for accessing Shizuku with a free (libre) license and closed the issue. It implicitly implies that <mark>the authors have no intention to make the app free,</mark> and therefore, being a strong supporter of freedom, I cannot add support for Shizuku.

As stated in [choosealicense.com][cal]:
> If you find software that doesn’t have a license, that generally means you have no permission from the creators of the software to use, modify, or share the software. Although a code host such as GitHub may allow you to view and fork the code, this does not imply that you are permitted to use, modify, or share the software for any purpose.
>
> Your options:
> - **Ask the maintainers nicely to add a license.** Unless the software includes strong indications to the contrary, lack of a license is probably an oversight. If the software is hosted on a site like GitHub, open an issue requesting a license and include a link to this site. If you’re bold and it’s fairly obvious what license is most appropriate, open a pull request to add a license – see “suggest this license” in the sidebar of the page for each license on this site (e.g., MIT).
> - **Don’t use the software.** Find or create an alternative that is under an open source license.
> - **Negotiate a private license.** Bring your lawyer.

I do have plans to work more on ADB and private APIs to make things a bit faster.

::: tip In Short
Due to the non-free nature of Shizuku, I can't add support for Shizuku.
:::

_See also:_
- _[Related discussion][shizuku_discussion]_
- _[What is free software?][free_sw]_

## What are bloatware and how to remove them?
Bloatware are the unnecessary apps supplied by the vendor or OEM and are usually system apps. These apps are often used to track users and collect user data which they might sell for profits. System apps do not need to request any permission in order to access device info, contacts and messaging data, and other usage info such as your phone usage habit and everything you stored on your shared storage(s). These bloatware may also include Google apps (such as Google Play Services, Google Play Store, Gmail, Google, Messages, Dialer, Contacts), Facebook apps (the Facebook app consists of four or five apps), Facebook Messenger, Instagram, Twitter and many other apps which can also track users and/or collect user data without consent given that they all are system apps. You can disable only a few permissions using Android Settings but be aware that Android Settings hides almost every permissions any security specialist would call potentially _dangerous_. If the bloatware were user apps, you could easily uninstall them either from Android Settings or AM. But uninstalling system apps is not possible without superuser permission. You can also uninstall apps using ADB but it may not work for all apps. AM can uninstall system apps with root or ADB (the latter with certain limitations, of course). But these methods cannot _remove_ the system apps completely as they are located in the _system_ partition which a read-only partition. If you have root, you can remount this partition to manually _purge_ these apps but this will break over the air (OTA) updates since data in the system partition has been modified. There are two kind of updates, delta (short in size, consisting of only changes) and full updates. You can still apply full updates but the bloatware will be installed again and consequently you have to delete them. Besides, not all vendors provide full updates. Another solution is to disable these apps either from Android Settings (no-root) or AM. But certain services can still run in the background as they can be started by other system apps using inter process communication (IPC). One possible solution is to disable all bloatware until the service has finally stopped (after a restart). But due to heavy modifications of the Android frameworks by the vendors, removing or disabling certain bloatware may cause the System UI to crash or even cause bootloop, thus, (soft) bricking your device. You may search the web or consult fellow users to find out more about how to debloat your device.

::: tip Note
In most cases, you cannot completely debloat your device. Therefore, it is recommended that you use a custom ROM free bloatware such as Graphene OS or Lineage OS.
:::

[shizuku]: https://shizuku.rikka.app
[shizuku_56]: https://github.com/RikkaApps/Shizuku/issues/56
[cal]: https://choosealicense.com/no-permission/
[shizuku_discussion]: https://github.com/MuntashirAkon/AppManager/issues/55
[free_sw]: https://www.gnu.org/philosophy/free-sw.html
