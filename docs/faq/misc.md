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

[shizuku]: https://shizuku.rikka.app
[shizuku_56]: https://github.com/RikkaApps/Shizuku/issues/56
[cal]: https://choosealicense.com/no-permission/
[shizuku_discussion]: https://github.com/MuntashirAkon/AppManager/issues/55
[free_sw]: https://www.gnu.org/philosophy/free-sw.html
