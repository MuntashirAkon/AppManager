# 1-Click Ops Page

This page appears after clicking on the **1-Click Ops** option in the [main menu](./main-page.md#options-menu). Currently supported operations include _block/unblock trackers_, _block components_ and _deny app ops_. More options will be added later.

::: details Table of Contents
[[toc]]
:::

## Block/Unblock Trackers
This option can be used to block or unblock ad/tracker components from the installed apps. After you click on this option, you will be asked to select if AM will list trackers from all apps or only from the user apps. Novice users should avoid blocking trackers from the system apps in order to avoid consequences. After that, a multi-choice dialog box will appear where you can deselect the apps you want to exclude from this operation. Clicking _block_ or _unblock_ applies the changes immediately.

::: warning Notice
Certain apps may not function as expected after applying the blocking. If that is the case, remove blocking rules all at once or one by one in the component tabs of the corresponding [App Details][1] page.
:::

_See also:_ 
- _[How to unblock the tracker components blocked using 1-Click Ops or Batch Ops?](../faq/app-components.md#how-to-unblock-the-tracker-components-blocked-using-1-click-ops-or-batch-ops)_
- _[App Details Page: Blocking Trackers](app-details-page.md#blocking-trackers)_

## Block Components…
This option can be used to block certain app components denoted by the signatures. App signature is the full name or partial name of the components. For safety, it is recommended that you should add a `.` (dot) at the end of each partial signature name as the algorithm used here chooses all matched components in a greedy manner. You can insert more than one signature in which case all signatures have to be separated by spaces. Similar to the option above, there is an option to apply blocking to system apps as well.

::: danger Caution
If you are not aware of the consequences of blocking app components by signature(s), you should avoid using this setting as it may result in boot loop or soft brick, and you may have to apply factory reset in order to use your OS.
:::

## Set Mode for App Ops…
This option can be used to configure certain [app operations](../tech/AppOps.md) of all or selected apps. You can insert more than one app op constants separated by spaces. It is not always possible to know in advance about all the app op constants as they vary from device to device and from OS to OS. To find the desired app op constant, browse the _App Ops_ tab in the [App Details][1] page. The constants are integers closed inside brackets next to each app op name. You can also use the app op names. You also have select one of the [modes](../tech/AppOps.md#mode-constants) that will be applied against the app ops.

::: danger Caution
Unless you are well informed about app ops and the consequences of blocking them, you should avoid using this feature as it may result in boot loop or soft brick, and you may have to apply factory reset in order to use your OS.
:::

[1]: ./app-details-page.md
