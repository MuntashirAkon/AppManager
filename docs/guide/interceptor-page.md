---
next: false
sidebarDepth: 2
---
# Interceptor Page
Interceptor can be used to intercept communication between apps using `Intent`. It works as a man-in-the-middle between the source and the destination apps. It offers a feature-complete user interface for editing `Intent`s.

::: tip Info
Development of interceptor is still in progress. It can only intercept activities for now.
:::

::: warning Warning
Interceptor only works for _implicit_ intents where the [app component][app_component] isn't specified.
:::

_See also:_
- _[Common Intents](https://developer.android.com/guide/components/intents-common)_
- _[Intents and Intent Filters](https://developer.android.com/guide/components/intents-filters)_

## Intent Filters
Intent filters are used by the apps to specify which tasks they are able to perform or which tasks they are going to perform using other apps. For example, when you're opening a PDF file using a file manager, the file manager will try to find which apps to open the PDF with. To find the right applications, the file manager will create an Intent with filters such as MIME type and ask the system to retrieve the matched applications that is able to open this filter. The system will search through the Manifest of the installed applications to match the filter and list the app components that are able to open this filter (in our case the PDF). At this, either the file manager will open the desired app component itself or use a system provided option to open it. If multiple app components are able to open it and no default is set, you may get a prompt where you have to choose the right app component.

### Action
Action specifies the generic action to perform such as `android.intent.action.VIEW`. Applications often declare the relevant actions in the Manifest file to catch the desired Intents. The action is particularly useful for broadcast Intent where it plays a vital rule. In other cases, it works as an initial way to filter out relevant app components. Generic actions such as `android.intent.action.VIEW` and `android.intent.action.SEND` are widely used by apps. So, setting this alone may match a large number of app components.

### Data
Data is originally known as URI (Uniform Resource Identifier) defined in [RFC 2396](http://www.faqs.org/rfcs/rfc2396.html). It can be web links, file location, or a special feature called _content_. Contents are an Android feature managed by the [content providers][providers]. Data are often associated with a [MIME type](#mime-type).

Examples:
```
http://search.disroot.org/?q=URI%20in%20Android%20scheme&categories=general&language=en-US
https://developer.android.com/reference/android/net/Uri
file:///sdcard/AppManager.apk
mailto:email@example.com
content://io.github.muntashirakon.AppManager.provider/23485af89b08d87e898a90c7e/AppManager.apk
```

### MIME Type
MIME type of the [data](#data). For example, if the data field is set to `file:///sdcard/AppManager.apk`, the associated MIME type can be `application/vnd.android.package-archive`.

### Categories
This is similar to [action](#action) in the sense that it is also used by the system to filter app components. This has no further benefits. Unlike _action_, there can be more than one category. Clicking on the _plus_ button next to the title allows adding more categories.

### Flags
Flags are useful in determining how system should behave during the launch or after the launch of an activity. An average user should avoid this as it requires some technical background. The _plus_ button next to the title can be used to add one or more flags.

### Extras
Extras are the key-value pairs used for supplying additional information to the destination component. You can add extras using the _plus_ button next to the title.

## URI
Represents the entire Intent as a URI (e.g. `intent://...`). Some data cannot be converted to string, and as a result, they might not appear here.

## Matching Activities
List all the activity components that matches the Intent. This is internally determined by the system (rather than AM). The launch button next to each component can be used to launch them directly from AM.

## Reset to Default
Reset the Intent to its initial state. This may not always work as expected.

## Send Edited Intent
Resend the edited Intent to the destination app. This may open a list of apps where you have to select the desired app. The result received from the target app will be sent to the source app. As a result the source app will not know if there was a man-in-the-middle.

[app_component]: ../faq/app-components.md#what-are-the-app-components
[providers]: ./app-details-page.md#providers