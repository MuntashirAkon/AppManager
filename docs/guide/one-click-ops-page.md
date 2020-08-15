# 1-Click Ops Page

This page appears after clicking on the **1-Click Ops** option in the [main menu](./main-page.md#options-menu). Currently supported operations include _block/unblock trackers_, _block components_ and _deny app ops_. More options will be added later.

::: details Table of Contents
[[toc]]
:::

## Block Trackers
This option can be used to block or unblock ad or tracker components from all the installed apps. When clicking on this option, you will be offered to select whether to list all apps or only the user apps. Novice users should select user apps only. After that, a multichoice dialog box will appear where you can deselect the apps you want to exclude from this operation. Clicking on _block_ or _unblock_ will apply the changes immediately.

::: warning Notice
Certain apps may not function as expected after applying the blocking. If that is the case, remove blocking rules all at once or one by one using the corresponding [App Details][1] page.

:::

_See also: [How to unblock the tracker components blocked using 1-Click Ops or Batch Ops?](../faq/app-components.md#how-to-unblock-the-tracker-components-blocked-using-1-click-ops-or-batch-ops)_

## Block Components…
This option can be used to block certain app components denoted by the signatures. App signature is the full name or partial name of the components. For safety, it is recommended that you should add a `.` (dot) at the end of each partial signature name as the algorithm used here chooses all matched components in a greedy manner. You can insert more than one signature in which case all signatures have to be separated by spaces. Similar to the option above, there is an option to apply blocking to system apps as well.

::: danger Caution
If you are not aware of the consequences of blocking app components by signature(s), you should avoid usinig this setting as it may result in boot loop or soft brick, and you may have to apply factory reset in order to use your OS.
:::

## Deny App Ops…
This option can be used to block certain [app operations](../tech/AppOps.md) of all or selected apps. You can insert more than one app op constants separated by spaces. It is not possible in advance to know all app op constants as they vary from device to device and OS to OS. To find the desired app op constant, browse the _App Ops_ tab in the [App Details][1] page. The constants are integers closed inside brackets next to each app op name.

::: danger Caution
Unless you are well informed about app ops and the consequences of blocking them, you should avoid using this feature as it may result in boot loop or soft brick, and you may have to apply factory reset in order to use your OS.
:::

[1]: ./app-details-page.md
